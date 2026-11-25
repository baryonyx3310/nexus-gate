#version 150
// Nexus Gate energy core as a voxel sphere: the quad is sampled on a coarse pixel grid, lighting and colour
// are posterised into a few flat tones, and the plasma animates frame-by-frame like a vanilla animated
// texture. vertexColor.rgb = tint, vertexColor.a = intensity. Open (0..1) splits the shell into petals
// whose cuts follow the pixel grid.

uniform float GameTime;
uniform float Open;
uniform vec4 ColorModulator;

in vec2 texCoord;
in vec4 vertexColor;

out vec4 fragColor;

const float PI = 3.14159265;
const float R = 0.62;
const float PETALS = 6.0;
const float GRID = 12.0;     // pixels across the whole quad (sphere is ~7.5 px wide)
const float FPS = 10.0;      // animation frame rate of the plasma
const float LEVELS = 5.0;    // tones per channel

float hash13(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.zyx + 31.32);
    return fract((p.x + p.y) * p.z);
}

float vnoise3(vec3 x) {
    vec3 i = floor(x);
    vec3 f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(hash13(i), hash13(i + vec3(1.0, 0.0, 0.0)), f.x),
                   mix(hash13(i + vec3(0.0, 1.0, 0.0)), hash13(i + vec3(1.0, 1.0, 0.0)), f.x), f.y),
               mix(mix(hash13(i + vec3(0.0, 0.0, 1.0)), hash13(i + vec3(1.0, 0.0, 1.0)), f.x),
                   mix(hash13(i + vec3(0.0, 1.0, 1.0)), hash13(i + vec3(1.0, 1.0, 1.0)), f.x), f.y), f.z);
}

float fbm3(vec3 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 3; i++) {
        v += a * vnoise3(p);
        p = p * 2.03 + vec3(1.7, 9.2, 4.1);
        a *= 0.5;
    }
    return v;
}

float steps(float v, float n) {
    return floor(clamp(v, 0.0, 1.0) * n + 0.5) / n;
}

vec3 shadeSphere(vec2 q, vec3 tint, float intensity, float t) {
    float rr = dot(q, q);
    float z = sqrt(max(1.0 - rr, 0.0));
    vec3 n = vec3(q, z);
    float ca = cos(t * 0.35);
    float sa = sin(t * 0.35);
    vec3 sp = vec3(n.x * ca - n.z * sa, n.y, n.x * sa + n.z * ca);
    float plasma = steps(fbm3(sp * 2.6 + vec3(0.0, t * 0.25, t * 0.4)), 3.0);
    float veins = 1.0 - abs(fbm3(sp * 4.0 - vec3(t * 0.3)) * 2.0 - 1.0);
    veins = step(0.82, veins);
    // Block-style lighting: three flat bands from the upper left.
    vec3 L = normalize(vec3(-0.55, 0.65, 0.6));
    float diff = steps(max(dot(n, L), 0.0), 3.0);
    vec3 deep = tint * 0.18;
    vec3 hot = mix(tint, vec3(1.0), 0.65);
    vec3 col = mix(deep, tint * 0.9, plasma) * (0.40 + 0.60 * diff);
    col += hot * veins * (0.5 + 0.9 * intensity);
    col += hot * step(0.93, z) * 0.7 * intensity;          // bright core pixels
    col += tint * step(0.72, sqrt(rr)) * (0.45 + 0.6 * intensity); // 1px lit outline
    return col;
}

void main() {
    float t = floor(GameTime * 1200.0 * FPS) / FPS;
    vec2 tc = (floor(texCoord * GRID) + 0.5) / GRID;
    vec2 p = tc * 2.0 - 1.0;
    float r = length(p);
    vec3 tint = vertexColor.rgb;
    float intensity = vertexColor.a;

    vec3 col = vec3(0.0);
    float alpha = 0.0;

    vec2 q = p / R;
    if (Open <= 0.001) {
        if (length(q) < 1.0) {
            col = shadeSphere(q, tint, intensity, t);
            alpha = 1.0;
        }
    } else {
        // Petal split: every sector slides outward along its bisector, in whole pixels.
        float ang = atan(q.y, q.x);
        float sector = floor((ang + PI) / (2.0 * PI / PETALS));
        float mid = -PI + (sector + 0.5) * (2.0 * PI / PETALS);
        vec2 dir = vec2(cos(mid), sin(mid));
        float push = Open * Open * 1.6;
        vec2 qs = (q - dir * push) / max(1.0 - Open * 0.35, 0.2);
        float angS = atan(qs.y, qs.x);
        float d = abs(mod(angS - mid + PI, 2.0 * PI) - PI);
        float halfW = PI / PETALS;
        if (length(qs) < 1.0 && d < halfW && dot(qs, dir) > 0.0) {
            col = shadeSphere(qs, tint, intensity, t);
            float crack = step(halfW - 0.22, d);
            col += mix(tint, vec3(1.0), 0.7) * crack * (1.2 + Open);
            alpha = 1.0 - step(0.9, Open);
        }
    }

    // Stepped halo: two rings of pixels around the sphere.
    float px = 2.0 / GRID;
    float halo = r - R < px ? 0.45 : (r - R < 2.0 * px ? 0.18 : 0.0);
    halo *= intensity * (1.0 - Open);
    if (alpha < 0.5 && halo > 0.0) {
        col = tint * halo * 1.6;
        alpha = halo;
    }
    alpha *= step(r, 0.96);
    col = floor(col * LEVELS + 0.5) / LEVELS;
    fragColor = vec4(col, clamp(alpha, 0.0, 1.0)) * ColorModulator;
}
