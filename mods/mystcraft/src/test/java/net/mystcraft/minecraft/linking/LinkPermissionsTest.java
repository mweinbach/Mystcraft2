package net.mystcraft.minecraft.linking;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class LinkPermissionsTest {
    private static final ResourceLocation HOME=new ResourceLocation("minecraft:overworld"),AGE=new ResourceLocation("mystcraft:age_1"),OTHER=new ResourceLocation("mystcraft:age_2");
    @Test void sourceAllWhitelistRestrictAndDirectionSemantics(){
        LinkPermissions data=new LinkPermissions();assertTrue(data.allows("Player",HOME,AGE));
        data.change("Player",false,LinkPermissions.Direction.ENTRY,null);assertFalse(data.allows("Player",HOME,AGE));
        data.change("Player",true,LinkPermissions.Direction.ENTRY,AGE);assertTrue(data.allows("Player",HOME,AGE));assertFalse(data.allows("Player",HOME,OTHER));
        data.change("Player",false,LinkPermissions.Direction.DEPART,HOME);assertFalse(data.allows("Player",HOME,AGE));
        data.change("Player",true,LinkPermissions.Direction.DEPART,null);assertTrue(data.allows("Player",HOME,AGE));
        data.change("Player",true,LinkPermissions.Direction.ENTRY,null);assertTrue(data.allows("Player",HOME,OTHER));
    }
    @Test void conflictsRestrictWinsAndPersistenceReadIsNondestructive(){
        CompoundTag root=new CompoundTag(),permit=new CompoundTag(),restrict=new CompoundTag();
        net.minecraft.nbt.ListTag values=new net.minecraft.nbt.ListTag();values.add(net.minecraft.nbt.StringTag.valueOf(AGE.toString()));permit.put("Player",values.copy());restrict.put("Player",values.copy());
        root.put("PermitEntry",permit);root.put("RestrictEntry",restrict);CompoundTag before=root.copy();
        assertFalse(LinkPermissions.load(root).allows("Player",HOME,AGE));assertEquals(before,root);
        LinkPermissions data=new LinkPermissions();data.change("Player",false,LinkPermissions.Direction.DEPART,null);data.change("Player",true,LinkPermissions.Direction.DEPART,HOME);
        var restored=LinkPermissions.load(data.save(new CompoundTag()));assertTrue(restored.allows("Player",HOME,AGE));assertFalse(restored.allows("Player",OTHER,AGE));
    }
    @Test void nativeDimensionIdentitiesPersistWithDeadFlags(){
        DimensionIdentityData data=new DimensionIdentityData();var identity=data.identity(HOME);data.dead(AGE,true);
        var restored=DimensionIdentityData.load(data.save(new CompoundTag()));assertEquals(identity,restored.identity(HOME));assertTrue(restored.dead(AGE));
    }
}
