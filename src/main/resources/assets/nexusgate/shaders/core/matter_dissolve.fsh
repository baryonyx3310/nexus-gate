#version 150
// Quantum dematerialisation of a body, pixel by pixel of its own texture.
// A texel disappears when its score drops under the dissolve front; the score mixes the height on the body
// (head first) with per-texel noise, so the body comes apart top-down in loose pixels and, run backwards,
// is rebuilt bottom-up. Texels right at the front burn as emissive cyan: matter being converted.

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float GameTime;

in vec2 texCoord0;
in vec4 params;
in vec4 lightColor;

out vec4 fragColor;

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

const float EDGE = 0.09;
const vec3 CYAN = vec3(0.20, 0.94, 1.00);
const vec3 WHITE = vec3(0.81, 0.98, 1.00);

void main() {
    vec4 tex = texture(Sampler0, texCoord0);
    if (tex.a < 0.1) discard;

    float height = params.r;
    float dissolve = params.a;
    vec2 texel = floor(texCoord0 * vec2(textureSize(Sampler0, 0)));
    float noise = hash12(texel + vec2(17.0, 3.0));
    float score = 0.6 * (1.0 - height) + 0.4 * noise;

    // Front runs from -EDGE (nothing gone, no edge) to 1 (everything gone, edge included).
    float front = dissolve * (1.0 + EDGE) - EDGE;
    if (score < front) discard;

    if (score < front + EDGE) {
        // Converting texel: flat emissive tones flickering frame by frame.
        float frame = floor(GameTime * 24000.0 / 2.0);
        float flick = step(0.5, hash12(texel + vec2(frame, frame * 0.37)));
        fragColor = vec4(mix(CYAN, WHITE, flick), 1.0);
        return;
    }

    vec4 col = tex * lightColor * ColorModulator;
    // The remaining body takes a faint cyan cast as the conversion advances.
    col.rgb = mix(col.rgb, col.rgb * 0.55 + CYAN * 0.45, dissolve * 0.35);
    fragColor = vec4(col.rgb, 1.0);
}
