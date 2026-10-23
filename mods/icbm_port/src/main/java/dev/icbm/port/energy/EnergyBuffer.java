package dev.icbm.port.energy;

import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.minecraft.nbt.NbtCompound;
import team.reborn.energy.api.EnergyStorage;
import team.reborn.energy.api.base.SimpleEnergyStorage;

/** Transactional energy with bounded persistence and separate machine action costs. */
public final class EnergyBuffer extends SimpleEnergyStorage {
  private final Runnable dirty;
  private final EnergyStorage insertOnly = new InsertOnlyView();

  public EnergyBuffer(long capacity, long maxInsert, long maxExtract, Runnable dirty) {
    super(capacity, maxInsert, maxExtract);
    this.dirty = dirty;
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
    long next = Math.max(0, Math.min(capacity, value));
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
    setAmount(nbt.getLong("Energy"));
  }
}
