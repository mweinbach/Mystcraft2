## Remaining blocks

These blocks are the upstream blocks, models, textures and screens. `RemainingBlockGameTests` covers the behaviour
below. Personal-chest explosion resistance is already covered by `ItemBlockHookGameTests` and is not repeated here.

A dimensional stabilizer loads the chunk it stands in, and that center cell cannot be turned off. Turning on one
more cell in the 5×5 grid adds that chunk and doubles the energy use, from the base 5,000 J per chunk per tick.
With energy in its buffer it stays active, outputs a redstone level of 15, and holds a ticking chunk ticket on
both chunks. Unpowered, it stays inactive, outputs 0, and holds no ticket. The ticket is requested on the tick
after the machine becomes active, because the chunk loader runs before the energy extract. On Fabric the ticking
flag is saved and names the ticket `mekanism_port:block_ticking`; it still does not enable random ticks, which is
the limit in [08-fabric-api-limits.md](08-fabric-api-limits.md).

A seismic vibrator spends 50 J a tick from its back while it can function. While that extract succeeds it is
active, its position is in `Mekanism.activeVibrators`, and every 40 ticks (including the first, while the ticker
is still 0) it emits `mekanism:seismic_vibration`. A sculk sensor beside it leaves the inactive phase. An
unpowered vibrator does neither. Its render box is the 1×2×1 column already checked in `RenderBoundsGameTests`.

An industrial alarm placed facing up stays idle, and its render box is the model shape rather than a full cube.
A redstone block beside it powers the alarm. The alarm becomes active and the render box becomes the full block,
which is the space the aura draws in.

A fuelwood heater burns one coal. The burn time is half of vanilla's, and each burn tick adds the configured heat.
The fuel slot empties, the heater becomes active, and its temperature rises by more than one kelvin. A resistive
heater accepts energy on its left and right, spends its configured usage (100 J a tick at the default) and turns
that into heat at the configured efficiency. It becomes active, reports that energy as used, and its temperature
rises by more than one kelvin. Heat on both is readable from any side.

A chargepad accepts energy through its back and its bottom. A Robit standing on it is filled to the Robit's
100,000 J capacity from the pad's per-operation transfer. A player standing on it is charged through the item
handler on the player: Forge provides that handler, and on Fabric `FabricPlayerInventory` attaches one that reads
the live main inventory, armor and offhand in that order and is not serialized. A dimension change removes that
player and then revives the same instance. The capability store becomes valid again, and the Fabric handler builds
a new optional because the old one stays empty. After that revival an energy tablet in the hotbar keeps gaining
energy, and the pad is active while either entity is in the pad's box and the buffer is not empty.

An electric pump spends 100 J a tick from its back. After its operation time it sucks the water source below into
the top output tank, 1,000 mB, and that fluid is visible on the top side. With water-source pumping left at the
default off, the source block stays. A fluidic plenisher takes water in through its top and energy through its
back. After its operation time the first pass places a source in the air below and removes 1,000 mB from the tank.

A formulaic assemblicator reads a crafting formula on its server tick. A formula with oak planks in the first
column is the sticks recipe. Auto mode can be turned on once that formula is loaded. With planks in the input
inventory and energy on any side, auto mode moves the planks onto the grid and, after the 40 tick craft, the
output slots hold four sticks.

A modification station spends 100 J a tick from its back for 40 ticks while an energy unit sits with a Meka-Tool.
The unit is consumed and the tool reports the energy unit installed.

A personal barrel has 54 slots. While it is public, automation can insert a diamond and read it back. Set to
private, automation inserts nothing. A manual slot write still keeps an emerald, and the diamond stays. Opening
the barrel sets the open block state. A personal chest with a solid block above consumes the interaction and does
not open a screen.

On the Fabric client, at noon with the HUD hidden, these blocks stand in a row on smooth stone: the dimensional
stabilizer, seismic vibrator, industrial alarm, fuelwood heater, resistive heater, chargepad, electric pump,
fluidic plenisher, formulaic assemblicator, modification station, personal chest and personal barrel. The alarm
is powered and draws its aura. The screens were not opened in that client.
