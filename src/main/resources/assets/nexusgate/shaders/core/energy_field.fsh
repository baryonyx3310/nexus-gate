#version 150
// Flowing energy ribbon. UV.x runs along the ribbon, UV.y across it.
// vertexColor.rgb = tint, vertexColor.a = intensity. Meant for additive blending.

uniform float GameTime;
uniform vec4 ColorModulator;

in vec2 texCoord;
in vec4 vertexColor;

out vec4 fragColor;

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

// Pixel-art version: the ribbon is cut into blocks along its length (with gaps, like segmented LEDs),
// four rows of pixels across, flat tones, and frame-by-frame animation.
void main() {
    float t = floor(GameTime * 1200.0 * 12.0) / 12.0;
    float u = floor(texCoord.x * 8.0) / 8.0;
    float row = floor(clamp(texCoord.y, 0.0, 0.999) * 4.0);
    float v = (row + 0.5) / 4.0;
    float edge = 1.0 - abs(v * 2.0 - 1.0);           // 0.25 (outer rows) or 0.75 (inner rows)
    float inner = step(0.5, edge);
    float flow = floor(fbm3(vec3(u * 5.0 - t * 2.2, v * 3.0, t * 0.35)) * 4.0 + 0.5) / 4.0;
    float spark = step(0.9, vnoise3(vec3(u * 16.0 - t * 6.0, row, t)));
    float gap = step(0.75, fract(texCoord.x * 2.0));   // dark notch every half unit
    vec3 col = vertexColor.rgb * (0.25 + 0.6 * inner) * (0.5 + flow);
    col += vec3(1.0) * inner * step(0.55, flow) * 0.5;
    col += mix(vertexColor.rgb, vec3(1.0), 0.6) * spark;
    float a = vertexColor.a * clamp((0.3 + 0.7 * inner) * (0.45 + flow), 0.0, 1.0) * (1.0 - 0.75 * gap);
    col = floor(col * 5.0 + 0.5) / 5.0;
    fragColor = vec4(col, a) * ColorModulator;
}
