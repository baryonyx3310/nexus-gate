#version 150
// Convergent space-time lens: the real frame (Sampler0 = copy of the scene) is pulled toward a focal point, as
// if space were collapsing into a region of extreme concentration. Drawn on a camera-facing disc; texCoord is
// the local position in the disc (-1..1, x along the camera's right, y along its up).
//   vertexColor.a = strength (0..1), .r = seed, .b = chromatic aberration amount.
//
// Direction: every pixel at radius r shows what lies FARTHER OUT, at rs = r^(1 - k) >= r. The image is
// therefore compressed toward the centre (it never magnifies or pushes outward). A spiral twist that grows
// toward the centre turns the compression into a funnel. Ripples travel INWARD. The mapping eases back to
// identity at the rim, so the disc has no edge. There is no dark sphere and no ring: only bent light and a
// faint pinpoint where everything converges. Offsets snap to 2-pixel steps (pixel-art look).

uniform sampler2D Sampler0;
uniform vec2 ScreenSize;
uniform float GameTime;

in vec2 texCoord;
in vec4 vertexColor;

out vec4 fragColor;

float hash11(float p) {
    p = fract(p * 0.1031);
    p *= p + 33.33;
    p *= p + p;
    return fract(p);
}

// Local position whose image is shown at local position p, for a compression k (0..0.95).
vec2 source(vec2 p, float r, float ang, float k, float twist) {
    float rs = pow(max(r, 1e-4), 1.0 - k);
    float a = ang + twist;
    return vec2(cos(a), sin(a)) * rs;
}

vec3 sampleLocal(vec2 p, vec2 src, float localPerPixel) {
    vec2 px = (src - p) / localPerPixel;
    px = floor(px / 2.0 + 0.5) * 2.0;
    return texture(Sampler0, (gl_FragCoord.xy + px) / ScreenSize).rgb;
}

void main() {
    vec2 p = texCoord;
    float r = length(p);
    if (r >= 1.0) discard;

    float strength = vertexColor.a;
    float seed = vertexColor.r * 97.0;
    float chroma = vertexColor.b;
    float t = GameTime * 1200.0;
    float ang = atan(p.y, p.x);

    // Irregular ripples running toward the centre (phase r*k + t: crests move inward as t grows).
    float wobble = sin(ang * 3.0 + seed + t * 0.7) * 2.0 + sin(ang * 5.0 - seed * 1.3) * 1.0;
    float ripple = sin(r * 26.0 + t * 8.0 + wobble) * 0.08 + sin(r * 11.0 + t * 3.5 + ang * 2.0 + seed) * 0.05;
    float jitter = (hash11(floor(t * 12.0) + seed) - 0.5) * 0.04;

    float fall = smoothstep(1.0, 0.55, r);                    // 0 at the rim: identity there
    float k = clamp(0.9 * strength * (1.0 + ripple + jitter), 0.0, 0.95) * fall;
    float twist = 1.6 * strength * fall * (1.0 - r) * (fract(seed) < 0.5 ? 1.0 : -1.0);
    float localPerPixel = max(abs(dFdx(texCoord.x)) + abs(dFdy(texCoord.x)), 1e-5);

    float ca = 0.06 * chroma;
    vec3 col;
    col.r = sampleLocal(p, source(p, r, ang, min(k * (1.0 + ca), 0.95), twist), localPerPixel).r;
    col.g = sampleLocal(p, source(p, r, ang, k, twist), localPerPixel).g;
    col.b = sampleLocal(p, source(p, r, ang, k * (1.0 - ca), twist), localPerPixel).b;

    // The light that converges on the focal point: a tiny pinpoint, no sphere, no horizon.
    float pin = exp(-r * r / (0.0025 + 0.004 * (1.0 - strength))) * strength * 0.8;
    pin = floor(pin * 4.0 + 0.5) / 4.0;
    col += vec3(0.81, 0.98, 1.00) * pin;

    fragColor = vec4(col, 1.0);
}
