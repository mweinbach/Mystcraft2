package dev.icbm.port.energy;

import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import net.fabricmc.fabric.api.transfer.v1.storage.StoragePreconditions;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.minecraft.nbt.NbtCompound;
import team.reborn.energy.api.EnergyStorage;
import team.reborn.energy.api.base.SimpleEnergyStorage;

/**
 * Transactional energy with bounded persistence and separate machine action costs.
 *
 * <p>Like MIT ICBM-Classic {@code lib/energy/storage/EnergyBuffer}, the capacity may be a live
 * supplier ({@code storageLimit}) and insertion may be gated ({@code withCanReceive}); the EMP
 * tower uses both. A capacity that shrinks below the stored amount keeps the stored energy (as
 * upstream does) but refuses further insertion until the amount falls below it.
 */
public final class EnergyBuffer extends SimpleEnergyStorage {
  private final Runnable dirty;
  private final EnergyStorage insertOnly = new InsertOnlyView();
  private LongSupplier dynamicCapacity;
  private BooleanSupplier canInsert = () -> true;

  public EnergyBuffer(long capacity, long maxInsert, long maxExtract, Runnable dirty) {
    super(capacity, maxInsert, maxExtract);
    this.dirty = dirty;
  }

  /** Use a live capacity, bounded by the constructor capacity as an absolute ceiling. */
  public EnergyBuffer withDynamicCapacity(LongSupplier supplier) {
    this.dynamicCapacity = supplier;
    return this;
  }

  /** Gate all insertion, external and internal (upstream {@code withCanReceive}). */
  public EnergyBuffer withInsertCondition(BooleanSupplier condition) {
    this.canInsert = condition == null ? () -> true : condition;
    return this;
  }

  @Override
  public long getCapacity() {
    return dynamicCapacity == null
        ? capacity
        : Math.max(0, Math.min(capacity, dynamicCapacity.getAsLong()));
  }

  @Override
  public boolean supportsInsertion() {
    return maxInsert > 0 && canInsert.getAsBoolean();
  }

  @Override
  public long insert(long maxAmount, TransactionContext transaction) {
    StoragePreconditions.notNegative(maxAmount);
    if (!canInsert.getAsBoolean()) return 0;
    long inserted = Math.min(maxInsert, Math.min(maxAmount, getCapacity() - amount));
    if (inserted <= 0) return 0;
    updateSnapshots(transaction);
    amount += inserted;
    return inserted;
  }

  /** Remove up to {@code value} without requiring the full amount (upstream ticking upkeep). */
  public long drain(long value) {
    long drained = Math.max(0, Math.min(amount, value));
    if (drained > 0) {
      amount -= drained;
      dirty.run();
    }
    return drained;
  }

  /**
   * The view other mods see through {@link EnergyStorage#SIDED}: they may charge the machine but
   * never drain it. The machine's own costs and launcher sharing use this buffer directly.
   */
  public EnergyStorage insertOnly() {
    return insertOnly;
  }

  private final class InsertOnlyView implements EnergyStorage {
    @Override
    public boolean supportsInsertion() {
      return EnergyBuffer.this.supportsInsertion();
    }

    @Override
    public long insert(long maxAmount, TransactionContext transaction) {
      return EnergyBuffer.this.insert(maxAmount, transaction);
    }

    @Override
    public boolean supportsExtraction() {
      return false;
    }

    @Override
    public long extract(long maxAmount, TransactionContext transaction) {
      return 0;
    }

    @Override
    public long getAmount() {
      return EnergyBuffer.this.getAmount();
    }

    @Override
    public long getCapacity() {
      return EnergyBuffer.this.getCapacity();
    }
  }

  @Override
  protected void onFinalCommit() {
    dirty.run();
  }

  public void setAmount(long value) {
    long next = Math.max(0, Math.min(getCapacity(), value));
    if (amount != next) {
      amount = next;
      dirty.run();
    }
  }

  public boolean consume(long cost) {
    if (cost < 0) throw new IllegalArgumentException("Negative energy cost");
    if (amount < cost) return false;
    if (cost > 0) {
      amount -= cost;
      dirty.run();
    }
    return true;
  }

  public long insert(long value, boolean simulate) {
    try (Transaction tx = Transaction.openOuter()) {
      long moved = insert(value, tx);
      if (!simulate) tx.commit();
      return moved;
    }
  }

  public long extract(long value, boolean simulate) {
    try (Transaction tx = Transaction.openOuter()) {
      long moved = extract(value, tx);
      if (!simulate) tx.commit();
      return moved;
    }
  }

  public void writeNbt(NbtCompound nbt) {
    nbt.putLong("Energy", amount);
  }

  public void readNbt(NbtCompound nbt) {
    // Against the absolute ceiling, not the live capacity: energy kept above a capacity that
    // shrank (an EMP tower whose radius was lowered) must survive a chunk reload too.
    long next = Math.max(0, Math.min(capacity, nbt.getLong("Energy")));
    if (amount != next) {
      amount = next;
      dirty.run();
    }
  }
}
