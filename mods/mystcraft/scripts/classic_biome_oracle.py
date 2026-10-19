# Independent rectangular GenLayerBiomeMyst -> ZoomMyst -> Smooth oracle.
MASK=(1<<64)-1
A=6364136223846793005;B=1442695040888963407

def mix(v,s):
 v=(v*(v*A+B)+s)&MASK
 return v-(1<<64) if v>>63 else v
class RNG:
 def __init__(self,w,x,z):
  self.w=w;self.c=w
  for s in (x,z,x,z):self.c=mix(self.c,s)
 def n(self,n):
  v=(self.c>>24)%n;self.c=mix(self.c,self.w);return v

def world(seed,salt):
 b=salt
 for _ in range(3):b=mix(b,salt)
 for _ in range(3):seed=mix(seed,b)
 return seed

def layer(seed,count,zooms):
 def base(x,z,w,h):return [RNG(world(seed,200),x+a,z+b).n(count) for b in range(h) for a in range(w)]
 f=base
 for salt in list(range(1000,1002))+list(range(1000,1000+zooms)):
  parent=f
  def zoom(x,z,w,h,parent=parent,salt=salt):
   pw=(w>>1)+3;ph=(h>>1)+3;p=parent(x>>1,z>>1,pw,ph);out=[0]*(pw*2*ph*2)
   for j in range(ph-1):
    for i in range(pw-1):
     a,b,c,d=p[i+j*pw],p[i+1+j*pw],p[i+(j+1)*pw],p[i+1+(j+1)*pw]
     r=RNG(world(seed,salt),((x>>1)+i)*2,((z>>1)+j)*2)
     low=(a,c)[r.n(2)];right=(a,b)[r.n(2)]
     if b==c==d:m=b
     elif a==b==c or a==b==d or a==c==d:m=a
     elif a==b and c!=d or a==c and b!=d or a==d and b!=c:m=a
     elif b==c and a!=d or b==d and a!=c:m=b
     elif c==d and a!=b:m=c
     else:m=(a,b,c,d)[r.n(4)]
     for dx,dz,v in ((0,0,a),(0,1,low),(1,0,right),(1,1,m)):out[(j*2+dz)*pw*2+i*2+dx]=v
   return [out[(j+(z&1))*pw*2+i+(x&1)] for j in range(h) for i in range(w)]
  f=zoom
 def smooth(x,z,w,h):
  p=f(x-1,z-1,w+2,h+2);out=[];pw=w+2
  for j in range(h):
   for i in range(w):
    a,b,c,d,e=p[i+(j+1)*pw],p[i+2+(j+1)*pw],p[i+1+j*pw],p[i+1+(j+2)*pw],p[i+1+(j+1)*pw]
    out.append((a,c)[RNG(world(seed,1000),x+i,z+j).n(2)] if a==b and c==d else a if a==b else c if c==d else e)
  return out
 return smooth
coords=[(-513,-129),(-17,31),(-1,-1),(0,0),(1,1),(16,-16),(127,73),(4096,-8192)]
for zoom in range(5):
 f=layer(123456789,7,zoom)
 print(zoom,[f(x,z,1,1)[0] for x,z in coords])
