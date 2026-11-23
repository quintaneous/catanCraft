// Compact software renderer for diagnostic views of the two coastal block volumes.
#include <algorithm>
#include <cmath>
#include <cstdint>
#include <cstdlib>
#include <fstream>
#include <iostream>
#include <vector>
#include <string>
using std::max; using std::min;
struct V {
    double x,y,z;
    V operator+(const V& a) const {return {x+a.x,y+a.y,z+a.z};}
    V operator-(const V& a) const {return {x-a.x,y-a.y,z-a.z};}
    V operator*(double t) const {return {x*t,y*t,z*t};}
};
double dot(V a,V b){return a.x*b.x+a.y*b.y+a.z*b.z;}
V unit(V a){return a*(1.0/std::sqrt(dot(a,a)));}
V cross(V a,V b){return {a.y*b.z-a.z*b.y,a.z*b.x-a.x*b.z,a.x*b.y-a.y*b.x};}
V mix(V a,V b,double t){return a*(1-t)+b*t;}
double clamp(double x){return std::clamp(x,0.0,1.0);}
uint32_t noise(int x,int y,int z){
    uint32_t n=(uint32_t)x*0x6c8e9cf5u ^ (uint32_t)y*0x85ebca6bu ^ (uint32_t)z*0x27d4eb2du;
    n=(n^(n>>16))*0x21f0aaadu; n=(n^(n>>15))*0x735a2d97u;return n^(n>>15);
}
const int W=112,H=64,D=96;
enum Material {VOID=0,WATER=1,GRASS=2,PLANT=3,LOG=4,LEAF=5};
struct Paint{V rgb;int material;};
std::vector<Paint> palette;
std::vector<unsigned char> volume(W*H*D);
int cell(int x,int y,int z){
    if (x<0||x>=W||y<0||y>=H||z<0||z>=D)return 0;
    return volume[(y*D+z)*W+x];
}
const V sun=unit({-.62,1.,-.58});
bool shadeAt(V p){
    for(double t=.35;t<30;t+=.85){
        V n=p+sun*t;int i=cell(floor(n.x),floor(n.y),floor(n.z));if(i&&palette[i].material!=WATER&&palette[i].material!=PLANT)return true;
    }return false;
}
V sky(V d,bool diorama){
    if(diorama) return mix({219,230,229},{236,240,233},clamp(-d.y));
    return mix({206,223,223},{107,166,195},clamp(d.y*1.55+.06));
}
V render(V o,V dir,bool diorama){
    V bg=sky(dir,diorama);double a=-1e10,b=1e10;int nAxis=0;
    double origin[3]={o.x,o.y,o.z},d[3]={dir.x,dir.y,dir.z},bound[3]={W,H,D};
    for(int i=0;i<3;i++){
        if(std::abs(d[i])<1e-10){if(origin[i]<0||origin[i]>=bound[i])return bg;continue;}
        double s=-origin[i]/d[i],e=(bound[i]-origin[i])/d[i];if(s>e)std::swap(s,e);
        if(s>a){a=s;nAxis=i;} b=min(b,e);
    }
    if(b<=max(0.0,a))return bg;
    double t=max(0.0,a)+1e-6; V p=o+dir*t; int c[3]={(int)floor(p.x),(int)floor(p.y),(int)floor(p.z)};
    int step[3];double next[3],dt[3];
    for(int i=0;i<3;i++){step[i]=d[i]>=0?1:-1;dt[i]=std::abs(d[i])<1e-10?1e20:std::abs(1/d[i]);next[i]=std::abs(d[i])<1e-10?1e20:((step[i]>0?c[i]+1:c[i])-origin[i])/d[i];}
    double wet=0;V waterPos={0,0,0};bool underwater=false;V color=bg;
    for(int steps=0;steps<320&&t<b-1e-7;steps++){
        int axis=next[0]<next[1]?(next[0]<next[2]?0:2):(next[1]<next[2]?1:2);
        int id=cell(c[0],c[1],c[2]);int m=palette[id].material;p=o+dir*t;
        if(id&&m==WATER){
            if(!underwater){underwater=true;waterPos=p;}wet+=min(b,next[axis])-t;
        }else if(id){
            V n={0,0,0};if(nAxis==0)n.x=-step[0];else if(nAxis==1)n.y=-step[1];else n.z=-step[2];
            // Plant cells use two intersecting thin sprigs, rather than rendering the full cell.
            bool visible=true;
            if(m==PLANT){double fx=p.x-floor(p.x),fz=p.z-floor(p.z),fy=p.y-floor(p.y);
                visible=std::abs(fx-.5)<.30||std::abs(fz-.5)<.30;visible=visible&&fy<.83;}
            if(visible){
                V paint=palette[id].rgb;
                double fy=p.y-floor(p.y);
                if(m==GRASS&&nAxis!=1&&fy<.84)paint={126,99,70};
                double grain=.968+((noise((int)floor(p.x*5.99),(int)floor(p.y*5.99),(int)floor(p.z*5.99))%103)/103.)*.056;
                grain*=.976+(noise(c[0],c[1],c[2])%101)/2100.;
                double light=.72+.33*max(0.,dot(n,sun));
                if(dot(n,sun)>0.05&&shadeAt(p+n*.012))light*=.77;
                if(nAxis!=1&&fy>.97)light+=.025;
                color=paint*(grain*light);break;
            }
        }
        c[axis]+=step[axis];t=next[axis];next[axis]+=dt[axis];nAxis=axis;
    }
    if(underwater){
        double rip=std::sin(waterPos.x*1.04+std::sin(waterPos.z*.72)*.85+waterPos.z*.52);
        V sea=mix({40,118,137},{61,150,155},.5+.5*std::sin(waterPos.x*.12+waterPos.z*.1));
        color=mix(sea,color,std::exp(-max(wet,.1)*.27));
        double reflect=.08+.34*std::pow(1.-std::abs(dir.y),3);
        color=mix(color,{170,209,211},reflect+.05*(rip>.84));
    }
    return color;
}
int main(int argc,char**v){
    if(argc!=13){std::cerr<<"volume palette ppm width height m eyeX eyeY eyeZ targetX targetY targetZ\n";return 2;}
    std::ifstream in(v[1],std::ios::binary);in.read((char*)volume.data(),volume.size());if(in.gcount()!=(long)volume.size())return 2;
    std::ifstream pp(v[2]);double r,g,b;int m;while(pp>>m>>r>>g>>b)palette.push_back({{r,g,b},m});
    if(palette.empty()||*std::max_element(volume.begin(),volume.end())>=palette.size())return 2;
    std::ofstream out(v[3],std::ios::binary);int width=atoi(v[4]),height=atoi(v[5]);bool aerial=atoi(v[6])==1;
    V eye={atof(v[7]),atof(v[8]),atof(v[9])},target={atof(v[10]),atof(v[11]),atof(v[12])};
    V look=unit(target-eye),right=unit(cross({0,1,0},look)),up=cross(look,right);
    out<<"P6\n"<<width<<" "<<height<<"\n255\n";double aspect=(double)width/height;
    for(int y=0;y<height;y++)for(int x=0;x<width;x++){
        double u=((x+.5)/width-.5)*2,v=(.5-(y+.5)/height)*2;
        V o=eye,d=look;if(aerial){o=eye+right*(u*87)+up*(v*87/aspect);}else{d=unit(look+right*(u*.74)+up*(v*.74/aspect));}
        V color=render(o,d,aerial);unsigned char pixel[3]={(unsigned char)std::clamp(color.x,0.,255.),(unsigned char)std::clamp(color.y,0.,255.),(unsigned char)std::clamp(color.z,0.,255.)};out.write((char*)pixel,3);
    }
}
