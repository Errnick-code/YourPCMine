#version 150

uniform sampler2D DiffuseSampler;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    fragColor = texture(DiffuseSampler, vec2(1.0 - texCoord.x, 1.0 - texCoord.y));
}
