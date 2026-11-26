#version 150
// One face of a quantum fluctuation. The UV carries two shape ids (floor(u), floor(v)); the face flips between
// them frame by frame, so the fragment never settles into one form. 8x8 pixel grid, flat tones, interference
// fringes running across the face and a rim split into cyan and magenta (chromatic instability).
// vertexColor.rgb = tint, vertexColor.a = intensity. Additive blending.

uniform float GameTime;
uniform vec4 ColorModulator;

in vec2 texCoord;
in vec4 vertexColor;

out vec4 fragColor;

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

bool inShape(int s, vec2 q) {
    vec2 a = abs(q);
    float m = a.x + a.y;
    float c = max(a.x, a.y);
    if (s == 0) return m > 0.5 && m < 1.0;                       // hollow diamond
    if (s == 1) return c > 0.55 && c < 1.0;                      // square frame
    if (s == 2) return min(a.x, a.y) < 0.26 && c < 1.0;          // cross
    if (s == 3) return m < 1.0 && a.x > 0.22;                    // diamond split in two
    return c < 0.3 || (a.x > 0.7 && a.y > 0.7);                  // core with four detached corners
}

void main() {
    float sa = floor(texCoord.x);
    float sb = floor(texCoord.y);
    vec2 l = fract(texCoord);
    vec2 cellId = floor(l * 8.0);
    vec2 q = (cellId + 0.5) / 8.0 * 2.0 - 1.0;

    float t = GameTime * 1200.0;
    float flip = step(0.5, hash12(vec2(sa * 7.0 + sb * 3.0, floor(t * 14.0))));
    int shape = int(mod(mix(sa, sb, flip), 5.0));
    if (!inShape(shape, q)) discard;

    // Some cells blink out: the fragment is never quite all there.
    if (hash12(cellId + floor(t * 20.0) + sa * 13.0) < 0.12) discard;

    float fringe = 0.5 + 0.5 * sin((q.x * 3.0 + q.y * 5.0) * 3.0 + t * 9.0 + sa * 2.0);
    fringe = floor(fringe * 3.0 + 0.5) / 3.0;
    float rim = step(0.74, max(abs(q.x), abs(q.y)));

    vec3 col = vertexColor.rgb * (0.5 + 0.5 * fringe);
    col = mix(col, q.x > 0.0 ? vec3(1.0, 0.25, 0.85) : vec3(0.2, 0.95, 1.0), rim * 0.6);
    col += vec3(1.0) * step(0.9, fringe) * 0.35;
    col = floor(col * 5.0 + 0.5) / 5.0;
    fragColor = vec4(col, vertexColor.a) * ColorModulator;
}
