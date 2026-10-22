## Supercritical phase shifter

A supercritical phase shifter (`SPSGameTests`) forms at 7×7×7. The face pattern leaves the twelve corner cells of
each face empty, frame casings stay on the marked edges, and ports replace the other face blocks. One port has a
supercharged coil on its inner side. A missing face block, a coil that touches no port, a 3×3×3 and a 2×2×2 stay
unformed and are not merged with a neighbour. Polonium enters through the port's gas handler. Hydrogen, antimatter,
a casing block and an unformed port accept none of it, and a simulated fill stores nothing. Energy inserted into a
port with no coil stays in that port and produces no antimatter. The same amount through the coil's port — one
millibucket of antimatter's worth of Forge Energy or Team Reborn Energy — turns 1,000 mB of polonium into 1 mB of
antimatter. The gas handler gives that antimatter back and leaves the rest of the polonium. Saved polonium,
antimatter and the idle port's energy come back after the blocks are removed and their NBT is loaded. The master's
render box is the structure. Antimatter's EMC stays the crafting cost of a thousand polonium pellets per pellet.
In the development client `/mek build sps` built that 7×7×7 shell. Structural glass shows the casing frame and
the empty corner cells of each face. The command places no port and no supercharged coil, and nothing was fed,
so the orb and the coil bolts were not drawn.
