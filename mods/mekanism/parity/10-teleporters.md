## Teleporters

A teleporter (`TeleporterGameTests`) frame is 3 wide and 4 tall. The teleporter block is the bottom centre, frame
blocks stand on both sides from that floor through the top row, and one more frame block closes the top. The portal
is the two blocks above the teleporter. A bare block reports no frame and uses the block's own box. A framed
teleporter with energy and no second machine on its frequency reports no link and does not draw a portal. Its render
box is the portal interior. Two framed teleporters on one public frequency, each holding energy, report ready, draw
the frequency's purple portal, and use that portal box. Breaking one frame block returns the first teleporter to no
frame.

Frequencies are public or private, which is all upstream 10.3.9 stores. A public frequency is shared. The same
private name for two owners does not link. Each owner keeps a separate frequency, and a player who never created
that name cannot read it. Trusted is the block's security mode, not a third frequency visibility. Setting an owner creates
that owner's security frequency, and the trusted list is who else may open the block. A trusted player can open the
teleporter and the quantum entangloporter, a stranger cannot, and trusted access does not hand over the owner's
private teleporter frequency.

An entity in a ready portal moves to the other portal in the same dimension. The destination remembers the arrival,
and the source spends energy. A pig sent through a public frequency arrives at a framed teleporter in the nether, at
that portal's position rather than a coordinate-scaled one. On Fabric, `Entity.changeDimension(ServerLevel, ITeleporter)`
asks `getPortalInfo` and then `placeEntity`. The reposition function moves the entity with `FabricDimensions.teleport`
and ignores the portal flag, so no nether portal is built. `playTeleportSound` is not consulted, so a player can still
hear the vanilla portal travel sound. Mekanism plays the enderman teleport sound itself. The portal particles are the
vanilla portal particle.

A portable teleporter looks the frequency up with the holder's identity, so another player's private frequency does
not move them. On a public frequency it moves its holder onto the framed teleporter and spends the item's energy.
The cost is the same formula as the block: a base cost, a cost per distance, and the dimension penalty.

Two quantum entangloporters with no frequency accept no energy, fluid, gas or items. Separate private inventory
frequencies do not share energy. One public inventory frequency shares energy, water, hydrogen and items. The input
side accepts them and the output side of the other block returns them. A simulated fill stores nothing, and the
output side rejects energy. Forge moves that energy as Forge Energy. Fabric moves it as Team Reborn Energy, at the
configured 2.5 J per FE, and moves the fluid and the items through the Transfer API. Hydrogen moves through the gas
handler on both loaders. The entangloporter screen is the upstream frequency selector with its energy and heat tabs.
In the Fabric development client a formed teleporter draws that purple portal inside the frame, and the quantum
entangloporter screen opens beside it with the frequency selector, the energy and heat tabs, and the inventory.
