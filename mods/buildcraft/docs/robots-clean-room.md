# Robots: a clean-room implementation

BuildCraft's robots, docking stations and redstone boards exist upstream only in the BuildCraft 7 trees
`src_old_license/` and `common_old_license/`, which are under the Minecraft Mod Public License (MMPL) rather than
the MPL-2.0 that covers this port's code. This port therefore does **not** derive its robot code from upstream.

## How the code was written

- No file in `src_old_license/` or `common_old_license/` was opened, copied, translated or paraphrased. The robot
  code (`ports.buildcraft.robotics` outside the zone planner, `robotics.board`, `robotics.statements`, the
  `RobotRenderer` and `DockingStationRenderer` clients and the robot GameTests) was written for this port from the
  publicly described behaviour of BuildCraft 7.1 robots: a robot item and flying entity that lives at a docking
  station on a pipe, redstone boards that give it a job, MJ energy charged at stations on kinesis pipes, and gate
  triggers and actions that report on and steer the robots at a station.
- It is built only on this port's own MPL-2.0 APIs (pipes, pluggables, kinesis power, the statement system,
  `BlockProtection`, map locations and zone plans), in the port's existing style. Names of classes, fields,
  constants and NBT keys are this port's own.
- Behavioural details that public descriptions leave open were decided here and are listed as deliberate
  differences in [PARITY.md](PARITY.md#robots).

## What is reused from upstream

Only MMPL-1.0.1 **assets** from upstream's `buildcraft_resources` tree, the same asset licence and tree the rest of
this port's assets already come from (see [UPSTREAM.md](../UPSTREAM.md#robot-assets) for the file list):

- robot skins (`textures/entities/robot_*.png`), board item textures (`textures/items/board/*.png`), docking station
  textures (`textures/station/*.png`) and the robot gate icons (`textures/triggers/*_robot_*`, `*_station_*`);
- the geometry and face UVs of `models/robot.json` and the plate-and-stem dimensions of
  `models/pluggables/robot_station_base.obj`, restated as vanilla item model elements and renderer boxes;
- the English names of boards, gate statements and items from `lang/en_US.lang`.
