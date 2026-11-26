#version 150
// Entity vertex stage for the matter dissolve. Vertex colour carries parameters, not a tint:
// Color.r = height of the vertex on the body (0 feet .. 1 head), Color.a = dissolve (0 intact .. 1 gone).
// Lighting is computed here like the vanilla entity shaders, but kept apart from the parameters.

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
out vec4 params;
out vec4 lightColor;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    texCoord0 = UV0;
    params = Color;
    vec3 n = normalize(Normal);
    float l0 = max(0.0, dot(normalize(Light0_Direction), n));
    float l1 = max(0.0, dot(normalize(Light1_Direction), n));
    float shade = min(1.0, (l0 + l1) * 0.6 + 0.4);
    lightColor = vec4(vec3(shade), 1.0) * texelFetch(Sampler2, UV2 / 16, 0);
}
