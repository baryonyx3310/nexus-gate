#version 150
// Space tunnel seen from inside the opened core. Depth speeds the flight; Exit (0..1) opens a
// growing aperture in the centre that reveals whatever lies behind it (alpha 0).

uniform float GameTime;
uniform float Depth;
uniform float Exit;
uniform float Aspect;
uniform float Radius;
uniform float CenterX;
uniform float CenterY;
uniform vec4 ColorModulator;

in vec2 texCoord;
in vec4 vertexColor;

out vec4 fragColor;

const float PI = 3.14159265;

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
    for (int i = 0; i < 4; i++) {
        v += a * vnoise3(p);
        p = p * 2.03 + vec3(1.7, 9.2, 4.1);
        a *= 0.5;
    }
    return v;
}

vec3 tunnel(vec2 p, float t, vec3 tint) {
    // Square-ish tunnel (blend of octagonal and round distance): reads as a corridor of blocks.
    vec2 ap = abs(p);
    float r = mix(length(p), max(max(ap.x, ap.y), (ap.x + ap.y) * 0.7071), 0.85);
    float a = atan(p.y, p.x);
    // Wrap-free angular coordinate: use direction instead of the raw angle for noise lookups.
    vec2 dir = p / max(r, 0.0001);
    float z = 0.42 / max(r, 0.015) + t * (1.2 + Depth * 7.0);
    float twist = z * 0.08 + t * 0.15;
    vec2 tw = vec2(dir.x * cos(twist) - dir.y * sin(twist), dir.x * sin(twist) + dir.y * cos(twist));
    float rings = step(0.82, 0.5 + 0.5 * sin(z * 6.2831));
    float bands = floor(fbm3(vec3(tw * 2.6, z * 0.45)) * 4.0 + 0.5) / 4.0;
    float streak = step(0.86, vnoise3(vec3(floor(tw * 10.0), floor(z * 2.0)))) * 1.6;
    float ribs = step(0.9, 0.5 + 0.5 * sin(a * 8.0 + z * 1.5)) * 0.35;
    vec3 hot = mix(tint, vec3(1.0), 0.55);
    vec3 col = tint * (0.08 + bands * 0.85);
    col += hot * (rings * 0.45 + ribs) * smoothstep(0.02, 0.4, r);
    col += vec3(0.85, 0.97, 1.0) * streak * smoothstep(0.05, 0.6, r) * (0.4 + Depth);
    col *= smoothstep(0.0, 0.22, r) * 0.9 + 0.1;
    col += vec3(1.0) * exp(-r * 14.0) * (0.6 + Depth * 0.8);
    return col;
}

void main() {
    float t = floor(GameTime * 1200.0 * 15.0) / 15.0;
    // Screen-space pixel grid: 96 pixels over the screen height (big chunky texels).
    const float GRID = 96.0;
    vec2 cell = vec2(GRID * Aspect, GRID);
    vec2 tc = (floor(texCoord * cell) + 0.5) / cell;
    vec2 p = tc * 2.0 - 1.0;
    p.x *= Aspect;
    p -= vec2(CenterX, CenterY);
    float r = length(p);
    vec3 tint = vertexColor.rgb;

    // Chromatic aberration in whole pixel offsets.
    float ca = floor(0.035 * r * (0.5 + Depth) * GRID * 0.5 + 0.5) / (GRID * 0.5);
    vec3 col;
    col.r = tunnel(p * (1.0 + ca), t, tint).r;
    col.g = tunnel(p, t, tint).g;
    col.b = tunnel(p * (1.0 - ca), t, tint).b;

    // Stepped vignette so the edges feel like the inside of the shell.
    col *= 1.0 - floor(smoothstep(0.9, 2.2, r) * 3.0) / 3.0 * 0.6;
    col = floor(col * 6.0 + 0.5) / 6.0;

    float alpha = vertexColor.a;
    if (Radius > 0.0) {
        // Circular aperture (the opened core). A bright lip marks the cut.
        float px = 2.0 / GRID;
        float lip = abs(r - Radius) < px ? 1.0 : 0.0;
        col += mix(tint, vec3(1.0), 0.7) * lip * 1.5;
        alpha *= max(step(r, Radius), lip);
    }
    if (Exit > 0.0) {
        // Exit aperture: an octagonal hole growing in pixel steps, with a bright 2px rim.
        vec2 ap = abs(p);
        float ro = max(max(ap.x, ap.y), (ap.x + ap.y) * 0.7071);
        float hr = Exit * Exit * 2.6;
        float px = 2.0 / GRID;
        float hole = step(hr, ro);
        float rim = (abs(ro - hr) < 2.0 * px ? 1.0 : 0.0) * (1.0 - Exit * 0.6);
        col += mix(tint, vec3(1.0), 0.6) * rim * 2.0;
        alpha *= max(hole, rim);
    }
    fragColor = vec4(col, clamp(alpha, 0.0, 1.0)) * ColorModulator;
}
