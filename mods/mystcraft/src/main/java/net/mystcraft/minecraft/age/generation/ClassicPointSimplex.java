package net.mystcraft.minecraft.age.generation;

import net.minecraft.util.RandomSource;

/** Classic 2D point sampler; expression order preserves native zero-threshold surface decisions. */
final class ClassicPointSimplex {
    private static final int[][] GRAD={{1,1},{-1,1},{1,-1},{-1,-1},{1,0},{-1,0},{1,0},{-1,0},{0,1},{0,-1},{0,1},{0,-1}};
    private final int[] permutation=new int[512];
    ClassicPointSimplex(RandomSource random){
        // Native sampler constructs three origins even though getValue does not use them.
        random.nextDouble();random.nextDouble();random.nextDouble();
        for(int i=0;i<256;i++)permutation[i]=i;
        for(int i=0;i<256;i++){
            int index=random.nextInt(256-i)+i,value=permutation[i];
            permutation[i]=permutation[index];permutation[index]=value;
            permutation[i+256]=permutation[i];
        }
    }
    double value(double x,double z){
        double skew=(x+z)*(.5*(Math.sqrt(3)-1));
        int i=floor(x+skew),j=floor(z+skew);
        double scale=(3-Math.sqrt(3))/6,unskew=(double)(i+j)*scale;
        double localX=x-((double)i-unskew),localZ=z-((double)j-unskew);
        int stepX=localX>localZ?1:0,stepZ=localX>localZ?0:1;
        double middleX=localX-(double)stepX+scale,middleZ=localZ-(double)stepZ+scale;
        double farX=localX-1+2*scale,farZ=localZ-1+2*scale;
        int pi=i&255,pj=j&255;
        double first=corner(permutation[pi+permutation[pj]]%12,localX,localZ);
        double middle=corner(permutation[pi+stepX+permutation[pj+stepZ]]%12,middleX,middleZ);
        double last=corner(permutation[pi+1+permutation[pj+1]]%12,farX,farZ);
        return 70*(first+middle+last);
    }
    private static int floor(double value){return value>0?(int)value:(int)value-1;}
    private static double corner(int index,double x,double z){
        double attenuation=.5-x*x-z*z;
        if(attenuation<0)return 0;
        attenuation=attenuation*attenuation;
        return attenuation*attenuation*((double)GRAD[index][0]*x+(double)GRAD[index][1]*z);
    }
}
