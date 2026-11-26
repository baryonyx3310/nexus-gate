#version 150
// Surface of a body under convergent collapse. The shape is squeezed on the CPU; nothing here is discarded, so
// the body never pops out: it is drawn until it is a single point.
//  - compression fronts: foci (3D Worley cells in ORIGINAL body space) ignite and spread; their moving fronts
//    are thin lines of emissive matter, and the matter behind them is already energy
//  - optical strain: the skin is sampled with a displacement and split colour channels that grow with the
//    compression, as if light crossing the body were bent toward the focal point
//  - as the body nears the point it becomes pure energy, so the last visible speck is a bright fluctuation
// Patterns snap to 1/16 block (model pixels).

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float GameTime;

in vec2 texCoord0;
in vec3 bodyPos;
in float instability;
in float conversion;
in vec4 lightColor;

out vec4 fragColor;

vec3 hash33(vec3 p) {
    p = fract(p * vec3(0.1031, 0.1030, 0.0973));
    p += dot(p, p.yxz + 33.33);
    return fract((p.xxy + p.yxx) * p.zyx);
}

float hash13(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.zyx + 31.32);
    return fract((p.x + p.y) * p.z);
}

const float CELL = 0.3;
const vec3 CYAN = vec3(0.20, 0.94, 1.00);
const vec3 WHITE = vec3(0.81, 0.98, 1.00);
const vec3 VIOLET = vec3(0.62, 0.42, 1.00);

void main() {
    vec4 tex = texture(Sampler0, texCoord0);
    if (tex.a < 0.1) discard;

    float c = conversion;
    float t = GameTime * 1200.0;
    float frame = floor(t * 12.0);
    vec3 P = floor(bodyPos * 16.0) / 16.0 + 1.0 / 32.0;

    // Distance to the nearest compression front (< 0 behind it).
    float field = 1e3;
    if (c > 0.0) {
        vec3 g = floor(P / CELL);
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    vec3 cell = g + vec3(x, y, z);
                    vec3 seed = (cell + hash33(cell)) * CELL;
                    float ignite = hash13(cell + 11.7) * 0.6;
                    float radius = max(0.0, (c - ignite) / (1.0 - ignite)) * 0.6;
                    if (radius > 0.0) field = min(field, distance(P, seed) - radius);
                }
            }
        }
    }
    float behind = 1.0 - smoothstep(-0.02, 0.0, field);       // already converted
    float front = 1.0 - smoothstep(0.0, 0.035, abs(field));   // the moving line itself

    // Optical strain on the skin, growing with the compression (and a sparse shimmer in phase A).
    float shimmer = instability * step(0.88, hash13(floor(P * 8.0) + frame * 0.5)) * 0.5;
    float strain = max(c, shimmer);
    vec2 ts = vec2(textureSize(Sampler0, 0));
    vec2 dir = hash33(floor(P * 8.0) + floor(t * 8.0)).xy - 0.5;
    vec2 off = floor(dir * strain * 5.0 + 0.5) / ts;
    vec3 col;
    col.r = texture(Sampler0, texCoord0 + off * 1.5).r;
    col.g = texture(Sampler0, texCoord0 + off).g;
    col.b = texture(Sampler0, texCoord0 - off * 0.5).b;
    col *= lightColor.rgb * ColorModulator.rgb;

    // Matter behind the fronts is energy; near the end the whole body is.
    float flick = hash13(P * 16.0 + frame);
    vec3 energy = flick < 0.5 ? CYAN : (flick < 0.88 ? WHITE : VIOLET);
    float energised = max(behind * 0.75, smoothstep(0.6, 0.95, c));
    col = mix(col, energy * (0.7 + 0.3 * flick), energised);
    col = mix(col, WHITE, front * step(0.001, c));
    fragColor = vec4(col, 1.0);
}
