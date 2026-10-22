## Lasers

The laser, laser amplifier and laser tractor beam (`LaserGameTests`) are the upstream blocks, models, textures and
screens. A laser accepts Forge Energy or Team Reborn Energy through its back and spends the configured usage,
10,000 J, along its facing each tick. The beam is the laser particle: a red translucent quad whose length is the
ray and whose width follows the energy in that shot, capped at 0.6. While `aestheticWorldDamage` is on, the energy
that reaches a block accumulates at 100,000 J per hardness. Dirt takes five ticks at the default usage. The break
is a fake player holding an atomic disassembler, so the block drops its normal loot, and the laser leaves that drop
in the world. A mob in the ray takes one half-heart per 2,500 J, with no hurt cooldown, and catches fire unless it
is immune to fire. A shield the mob is blocking with, and laser-dissipation modules on its armour, are the upstream
reductions of that damage.

A laser amplifier is a receptor, so the ray fills its buffer instead of digging the amplifier. The buffer stays put
while it is below the minimum threshold. Each shot is capped at the maximum threshold: with the minimum open and the
maximum at one joule, one tick spends one joule and the block is still there; opening the maximum then spends the
buffer and breaks it. The redstone output mode is off, entity detection or energy contents. Off stays at zero while
the buffer holds energy. Energy contents follows how full the buffer is. Entity detection outputs fifteen while a
mob stands in a shot. The screen is the upstream amplifier screen: energy gauge, delay, minimum and maximum fields,
and the redstone-output tab. It was not opened in the client.

A laser tractor beam is the same receptor with a 27-slot inventory. One shot carrying the block's break cost breaks
the block and puts the drop in that inventory, and the drop is not left on the ground. Its screen is the upstream
tractor-beam inventory. It was not opened in the client.

A formed fusion reactor takes the same ray through the laser focus matrix on its bottom centre. The matrix's
receptor adds the energy to the reactor the controller masters, and that controller's plasma temperature rises.
Igniting the burn with a full hohlraum stays in `FusionReactorGameTests`. The ray itself is still the laser's clip:
Forge 43.5.0 builds that clip with an empty collision context when the entity is null, and the Fabric build matches
it there.

On the Fabric client, at noon with the HUD hidden, a charged creative energy cube feeds a laser, the laser fills an
amplifier whose shot is capped at 60,000 J, and that shot draws the red beam into bedrock. The beam crosses the
frame in daylight, with sky above the wall and the machines at the near end. The amplifier and tractor-beam screens
were not opened in that client.
