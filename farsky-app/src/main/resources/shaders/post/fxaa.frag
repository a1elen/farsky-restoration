#version 110

// FXAA 3.11 (console version): luma based edge smoothing of the final frame.
uniform sampler2D texture;
uniform vec2 texelSize;

const float lumaRed = 0.299;
const float lumaGreen = 0.587;
const float lumaBlue = 0.114;

void main(){
	vec2 uv = gl_TexCoord[0].st;
	vec3 rgbNW = texture2D(texture, uv + vec2(-1.0, -1.0) * texelSize).rgb;
	vec3 rgbNE = texture2D(texture, uv + vec2(1.0, -1.0) * texelSize).rgb;
	vec3 rgbSW = texture2D(texture, uv + vec2(-1.0, 1.0) * texelSize).rgb;
	vec3 rgbSE = texture2D(texture, uv + vec2(1.0, 1.0) * texelSize).rgb;
	vec4 texel = texture2D(texture, uv);
	vec3 rgbM = texel.rgb;

	float lumaNW = dot(rgbNW, vec3(lumaRed, lumaGreen, lumaBlue));
	float lumaNE = dot(rgbNE, vec3(lumaRed, lumaGreen, lumaBlue));
	float lumaSW = dot(rgbSW, vec3(lumaRed, lumaGreen, lumaBlue));
	float lumaSE = dot(rgbSE, vec3(lumaRed, lumaGreen, lumaBlue));
	float lumaM = dot(rgbM, vec3(lumaRed, lumaGreen, lumaBlue));
	float lumaMin = min(lumaM, min(min(lumaNW, lumaNE), min(lumaSW, lumaSE)));
	float lumaMax = max(lumaM, max(max(lumaNW, lumaNE), max(lumaSW, lumaSE)));

	vec2 dir;
	dir.x = -((lumaNW + lumaNE) - (lumaSW + lumaSE));
	dir.y = ((lumaNW + lumaSW) - (lumaNE + lumaSE));

	float dirReduce = max((lumaNW + lumaNE + lumaSW + lumaSE) * 0.03125, 0.0078125);
	float rcpDirMin = 1.0 / (min(abs(dir.x), abs(dir.y)) + dirReduce);
	dir = clamp(dir * rcpDirMin, vec2(-8.0, -8.0), vec2(8.0, 8.0)) * texelSize;

	vec3 rgbA = 0.5 * (texture2D(texture, uv + dir * (1.0 / 3.0 - 0.5)).rgb + texture2D(texture, uv + dir * (2.0 / 3.0 - 0.5)).rgb);
	vec3 rgbB = rgbA * 0.5 + 0.25 * (texture2D(texture, uv - dir * 0.5).rgb + texture2D(texture, uv + dir * 0.5).rgb);
	float lumaB = dot(rgbB, vec3(lumaRed, lumaGreen, lumaBlue));
	if (lumaB < lumaMin || lumaB > lumaMax){
		gl_FragColor = vec4(rgbA, texel.a);
	} else {
		gl_FragColor = vec4(rgbB, texel.a);
	}
}
