#version 150
// Spatial rift: a spiralling tear with chromatic fringes around a dark centre.
// vertexColor.rgb = tint, vertexColor.a = life (0..1).

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

// Pixel-art version: 24x24 grid, octagonal distance, stepped bands, frame-by-frame animation.
void main() {
    float t = floor(GameTime * 1200.0 * 12.0) / 12.0;
    const float GRID = 24.0;
    vec2 p = (floor(texCoord * GRID) + 0.5) / GRID * 2.0 - 1.0;
    vec2 ap = abs(p);
    float r = max(max(ap.x, ap.y), (ap.x + ap.y) * 0.7071);   // octagon
    float a = atan(p.y, p.x);
    float k = vertexColor.a;
    float spiral = step(0.5, 0.5 + 0.5 * sin(a * 4.0 + r * 14.0 - t * 9.0));
    float jitter = vnoise3(vec3(floor(p * 6.0), floor(t * 4.0)));
    float px = 2.0 / GRID;
    float ringR = 0.55 + px * floor(jitter * 2.0);
    float dr = r - ringR;
    float ring = abs(dr) < px ? 1.0 : (abs(dr) < 2.0 * px ? 0.45 : 0.0);
    vec3 col = vertexColor.rgb * (0.6 + 0.8 * spiral) * ring;
    col.r += (dr > px && dr < 2.0 * px) ? 0.7 : 0.0;          // stepped chromatic fringe
    col.b += (dr < -px && dr > -2.0 * px) ? 0.9 : 0.0;
    float dark = dr < -px ? 1.0 : 0.0;
    float alpha = clamp(ring * (0.5 + 0.5 * spiral) + dark * 0.85, 0.0, 1.0) * k * step(r, 0.92);
    col *= 1.0 - dark;
    col = floor(col * 5.0 + 0.5) / 5.0;
    fragColor = vec4(col, alpha) * ColorModulator;
}
