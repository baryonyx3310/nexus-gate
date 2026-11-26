#version 150
// Entity vertex stage of the matter collapse. The geometry has already been compressed toward the focal point
// on the CPU (CollapseField); this stage only decodes the parameters carried by the vertex colour:
//   rgb = ORIGINAL position of the vertex on the body, relative to the feet (x,z: -0.75..0.75, y: 0..2.25), so
//         surface patterns stay glued to the body while it is squeezed
//   a   = body state: 0..37/255 = phase-A instability only, 38..255/255 = compression 0..1
// Lighting matches the vanilla entity shaders.

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

uniform sampler2D Sampler2;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec3 Light0_Direction;
uniform vec3 Light1_Direction;

out vec2 texCoord0;
out vec3 bodyPos;
out float instability;
out float conversion;
out vec4 lightColor;

void main() {
    float a = Color.a * 255.0;
    conversion = a >= 37.5 ? clamp((a - 38.0) / 217.0, 0.0, 1.0) : 0.0;
    instability = a >= 37.5 ? 1.0 : a / 37.0;
    bodyPos = vec3((Color.r - 0.5) * 1.5, Color.g * 2.25, (Color.b - 0.5) * 1.5);

    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    texCoord0 = UV0;

    vec3 n = normalize(Normal);
    float l0 = max(0.0, dot(normalize(Light0_Direction), n));
    float l1 = max(0.0, dot(normalize(Light1_Direction), n));
    float shade = min(1.0, (l0 + l1) * 0.6 + 0.4);
    lightColor = vec4(vec3(shade), 1.0) * texelFetch(Sampler2, UV2 / 16, 0);
}
