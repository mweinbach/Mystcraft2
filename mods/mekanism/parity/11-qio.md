## QIO

A QIO frequency (`QIOGameTests`) is public or private, which is all upstream 10.3.9 stores. A public name is one
frequency. The same private name for two owners is two frequencies, and a player who never created that name cannot
read it. A drive array on the frequency holds the drives. A basic drive stores 16,000 items and 128 types. The
hyper-dense, time-dilating and supermassive drives are the larger upstream tiers.

An importer facing a chest reads the chest through the loader's item inventory: Forge's item handler, and on Fabric
the Transfer API. With no filter installed it takes every stack. One operation moves one item type and up to 64
items, then waits ten ticks. A speed upgrade raises both limits the way upstream does. An item filter, built on the
same item, tag and mod filter windows the logistical sorter uses, takes the matching item and leaves the rest in the
chest. The window adds the QIO fuzzy toggle. The exporter uses that same inventory and those same limits. Export
without a filter stays off until it is turned on. With it on, or with an item filter, the stored items return to the
chest behind the exporter and the frequency count returns to zero.

The items live on the drive. Its metadata count is the number stored. Taking the drive out of the array takes the
items with it, and putting that drive back returns them to the frequency. A second drive array with no drive of its
own, a dashboard and a portable dashboard set to the same public frequency all see that count. The portable
dashboard takes the frequency's indigo color. A redstone adapter watching iron, with its trigger set to the stored
amount, outputs a signal of 15 while that iron is stored and 0 after the exporter has moved it.

The dashboard screen is the upstream item viewer: the frequency name, the stored item slots, search, sort and the
crafting window. The drive array draws a drive in each filled slot. The status texture is offline, ready, near full
or full, and it changes with the drive's count. The redstone adapter's torch lights while the adapter is powering.
In the Fabric development client the dashboard screen shows the items stored on the frequency.
