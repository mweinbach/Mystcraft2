package ports.jurassicraft.entity;

import ports.jurassicraft.content.Species;

/** Values from the original species metadata. Growth duration is in game ticks. */
public record DinosaurProfile(float babyWidth, float adultWidth, float babyHeight, float adultHeight,
        double babyHealth, double adultHealth, double speed, double babyDamage, double adultDamage,
        int growthTicks, float babyScale, float adultScale, float offsetY, float offsetZ,
        boolean carnivore, boolean timid, int eggPrimary, int eggSecondary) {
    public static DinosaurProfile of(Species s) {
        return switch (s) {
            case VELOCIRAPTOR -> new DinosaurProfile(.5f, 1f, .5f, 1.8f, 10,35,.3,1,8,45*24000,.3f,1.3f,0,0,true,false,0xA06238,0x632D11);
            case COELACANTH -> new DinosaurProfile(.1f,1f,.1f,1f,3,10,.4,.5,3,30*24000,.22f,1.8f,1.1f,-.2f,false,true,0x707B94,0x3B4963);
            case MICRORAPTOR -> new DinosaurProfile(.2f,.7f,.25f,.6f,4,10,.2,.5,2,30*24000,.15f,.4f,0,0,true,false,0x142146,0x101625);
            case BRACHIOSAURUS -> new DinosaurProfile(.9f,6.5f,1.5f,7f,20,150,.22,5,15,85*24000,.3f,2.5f,0,1,false,false,0x87987F,0x607343);
            case MUSSAURUS -> new DinosaurProfile(.25f,1f,.2f,.9f,2,15,.32,1,2,30*24000,.1f,.6f,0,.5f,false,true,0x6F9845,0x211F16);
            case DILOPHOSAURUS -> new DinosaurProfile(.4f,1.2f,.5f,1.9f,10,30,.4,2,6,40*24000,.22f,.95f,0,0,true,false,0x6B7834,0x2A2E30);
            case GALLIMIMUS -> new DinosaurProfile(.3f,1.2f,.55f,2.25f,5,30,.3,1,5,35*24000,.2f,.85f,0,0,false,true,0xD5BA86,0xD16918);
            case PARASAUROLOPHUS -> new DinosaurProfile(.5f,2.5f,.8f,3.5f,10,55,.41,2,8,45*24000,.4f,1.6f,0,.6f,false,false,0x9F8138,0x422306);
            case TRICERATOPS -> new DinosaurProfile(.35f,2.5f,.6f,3f,10,70,.35,2,10,45*24000,.325f,1.35f,.45f,0,false,false,0x404138,0x1C1C1C);
            case TYRANNOSAURUS -> new DinosaurProfile(.45f,3f,.8f,3f,10,80,.42,5,20,60*24000,.35f,2.5f,0,0,true,false,0x4E502C,0x353731);
        };
    }
}
