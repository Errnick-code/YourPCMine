#version 150

flat in vec4 passColor;

out vec4 fragColor;

float hash21(vec2 p) {
    p = fract(p * vec2(443.897, 441.423));
    p += dot(p, p + 19.19);
    return fract((p.x + p.y) * p.x);
}

float hash11(float p) {
    p = fract(p * 0.1031);
    p *= p + 33.33;
    p *= p + p;
    return fract(p);
}

void main() {
    // Распаковываем время из R,G,B (упаковано как 24-bit int / 1000.0)
    float r = round(passColor.r * 255.0);
    float g = round(passColor.g * 255.0);
    float b = round(passColor.b * 255.0);
    float timeMs = r * 65536.0 + g * 256.0 + b;
    float time = timeMs / 1000.0;

    // guiScale из alpha: 64→1, 128→2, 192→3, 255→4
    float guiScale = max(1.0, round(passColor.a * 255.0 / 64.0));

    // Размер зерна в физических пикселях экрана
    // grain=12 при guiScale=2 даёт блоки ~6 GUI пикселей — крупно как у ТВ
    float grain = 12.0 * guiScale;

    vec2 uv = floor(gl_FragCoord.xy / grain) * grain;

    float frameSeed = hash11(time * 7.351);

    // Основной шум
    float noise = hash21(uv + frameSeed * 1000.0);

    // Крупные хлопья (в 2.5x крупнее)
    vec2 uv2 = floor(gl_FragCoord.xy / (grain * 2.5)) * (grain * 2.5);
    float noiseCoarse = hash21(uv2 + frameSeed * 500.0);

    float combined = noise * 0.55 + noiseCoarse * 0.45;

    // Горизонтальные полосы
    float scanline = 0.95 + 0.05 * hash21(vec2(floor(gl_FragCoord.y / grain) * grain, frameSeed * 333.0));

    // Редкие вспышки
    float spark = step(0.992, hash21(uv + frameSeed * 3333.0));

    float value = clamp(combined * scanline + spark * 0.5, 0.0, 1.0);

    // CRT blue-tint
    fragColor = vec4(value * 0.90, value * 0.93, value, 1.0);
}
