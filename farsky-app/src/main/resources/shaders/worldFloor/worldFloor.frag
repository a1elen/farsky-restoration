#version 110

// Light
uniform vec3 light_ambient;
uniform vec3 light_diffuse;
uniform vec3 light_specular;

// Texture Params
uniform sampler3D colorTex;
uniform sampler3D normalTex;
uniform sampler3D causticTex;

// caustic params
uniform bool useCaustic;
uniform float causticX;
uniform float causticY;
uniform float causticZoomX;
uniform float causticZoomY;
uniform float causticAlpha;

// Other Params
uniform float lightLimit;
uniform float visibleLimit;
uniform vec3 glowColor;
varying vec3 lightDir, eyeVec;
varying float d;
// World space geometric normal of the chunk, used for the shadow slope bias
varying vec3 worldNormal;

// Shadow map (top down light, see game.shadow.ShadowMap)
uniform bool shadowEnabled;
uniform bool shadowSoft;
uniform float shadowTexel;
uniform float shadowWorldTexel;
uniform float shadowDarkness;
uniform mat4 shadowMatrix;
uniform sampler2D shadowTex;
varying vec4 shadowCoord;
varying vec3 vWorldPos;

// Alpha color
uniform vec3 alphaColor;
uniform vec3 alphaAbyssColor;


vec3 seaGradientColor(vec3 color){
	return mix(glowColor, color.rgb, clamp((visibleLimit-d)/400.0,0.1, 1.0));
}

// 1.0 = fully lit, 0.0 = fully shadowed (PCF when the shadow map is soft)
float getShadow(){
	if (!shadowEnabled) return 1.0;
	if (shadowCoord.w == 0.0) return 1.0;
	// Normal offset: lift the lookup point along the surface normal so faces
	// that run nearly parallel to the light rays (steep terrain) sample from in
	// front of themselves instead of shadow acneing into stripes.
	float normalLen = max(length(worldNormal), 0.0001);
	vec3 nrm = worldNormal / normalLen;
	vec3 offsetPos = vWorldPos + nrm * (shadowWorldTexel * 1.5);
	vec4 shadowClip = shadowMatrix * vec4(offsetPos, 1.0);
	if (shadowClip.w == 0.0) return 1.0;
	vec3 proj = shadowClip.xyz / shadowClip.w;
	if (proj.x < -1.0 || proj.x > 1.0 || proj.y < -1.0 || proj.y > 1.0) return 1.0;
	if (proj.z > 1.0 || proj.z < -1.0) return 1.0;

	vec2 uv = proj.xy * 0.5 + 0.5;
	float depth = proj.z * 0.5 + 0.5;
	// The normal offset above does most of the work; a small residual slope term
	// keeps the last traces of acne away on very steep faces.
	float cosTheta = clamp(nrm.y, 0.0, 1.0);
	float bias = 0.0012 + 0.0015 * (1.0 - cosTheta);

	if (shadowSoft){
		float lit = 0.0;
		for (int y = -1; y <= 1; y++){
			for (int x = -1; x <= 1; x++){
				vec2 offset = vec2(float(x), float(y)) * shadowTexel;
				float sampleDepth = texture2D(shadowTex, uv + offset).r;
				if (depth - bias <= sampleDepth) lit += 1.0;
			}
		}
		return lit / 9.0;
	}

	float sampleDepth = texture2D(shadowTex, uv).r;
	return depth - bias <= sampleDepth ? 1.0 : 0.0;
}

void main(){
	vec4 color = vec4(0,0,0,0);
	vec4 finalColor = vec4(0,0,0,0);
	vec3 bump = vec3(0,0,0);
	
	if (d<visibleLimit){
		if (gl_Color.r>0.0){
			color += (texture3D(colorTex, vec3(gl_TexCoord[0].st,0) + vec3(0,0,0.05)).rgba)*gl_Color.r;
			bump += (texture3D(normalTex, vec3(gl_TexCoord[1].st,0) + vec3(0,0,0.05)).rgb - 0.5)*gl_Color.r;
		}
		if (gl_Color.g>0.0){
			color += (texture3D(colorTex, vec3(gl_TexCoord[0].st,0) + vec3(0,0,0.30)).rgba)*gl_Color.g;
			bump += (texture3D(normalTex, vec3(gl_TexCoord[1].st,0) + vec3(0,0,0.30)).rgb - 0.5)*gl_Color.g;
		}
		if (gl_Color.b>0.0){
			color += (texture3D(colorTex, vec3(gl_TexCoord[0].st,0) + vec3(0,0,0.55)).rgba)*gl_Color.b;
			bump += (texture3D(normalTex, vec3(gl_TexCoord[1].st,0) + vec3(0,0,0.55)).rgb - 0.5)*gl_Color.b;
		}
		if (gl_Color.a>0.0){
			color += (texture3D(colorTex, vec3(gl_TexCoord[0].st,0) + vec3(0,0,0.80)).rgba)*gl_Color.a;
			bump += (texture3D(normalTex, vec3(gl_TexCoord[1].st,0) + vec3(0,0,0.80)).rgb - 0.5)*gl_Color.a;
		}
		
		// Ambient light only: the player is no longer used as a light source.
		vec3 light_color = light_ambient;
		light_color += normalize(lightDir).z/20.0;
		finalColor = color * vec4(light_color,1);
		
		
		// Alpha Color
		// Rock
		if (gl_Color.g>0.0){
			finalColor.rgb = finalColor.rgb + (1.0-min(finalColor.a,1.0)) * alphaColor;
			finalColor.a=1.0;
		}
		// Abyss
		if (gl_Color.a>0.0){
			finalColor.rgb = finalColor.rgb + (1.0-min(finalColor.a,1.0)) * alphaAbyssColor;
			finalColor.a=1.0;
		}
		
		// Caustic
		if (useCaustic){
			finalColor.rgb += texture3D(causticTex, vec3(gl_TexCoord[1].st, 0.25) * vec3(causticZoomX, causticZoomY,1.0) + vec3(causticX, causticY,0.0) ).rgb * causticAlpha;
			finalColor.rgb += texture3D(causticTex, vec3(gl_TexCoord[1].st, 0.75) * vec3(causticZoomX, causticZoomY,1.0) + vec3(-causticX, -causticY,0.0) ).rgb * causticAlpha;
		}
		
		// Shadow from the light coming straight from above
		finalColor.rgb *= mix(1.0 - shadowDarkness, 1.0, getShadow());
		
		
		// Gradient blur
		finalColor.a = min(finalColor.a,(visibleLimit-d)/40.0);
		
		// Water Glow Color Interpolation
		finalColor.rgb = seaGradientColor(finalColor.rgb);
	}
	
	gl_FragColor = finalColor;
}