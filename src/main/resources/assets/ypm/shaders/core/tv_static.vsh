#version 150

in vec3 Position;
in vec4 Color;

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec2 GlintAlpha;
    mat4 TextureMat;
};

layout(std140) uniform Projection {
    mat4 ProjMat;
};

flat out vec4 passColor;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    // flat — все фрагменты получают цвет первой вершины без интерполяции
    passColor = Color;
}
