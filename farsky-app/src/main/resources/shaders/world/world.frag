#version 110

// Light
uniform vec3 light_ambient;
uniform vec3 light_diffuse;
uniform vec3 light_specular;

// Texture Params
uniform sampler2D colorTex;

// Main Params
uniform bool directColor;
uniform bool topLight;
uniform bool emissive;
uniform bool highlight;

// Light params
uniform float lightLimit;
uniform float visibleLimit;
uniform vec3 glowColor;

// Alpha light param
uniform float alphaLightPercent;
uniform vec3 alphaLightcolor;
uniform bool invertAlphaLight;

// Wave
uniform bool useAlphaAsHeight;

// Other Params
uniform bool discardTransparency,selected;
uniform float selectedFactor;

// Shadow map (top down light, see game.shadow.ShadowMap)
uniform bool shadowEnabled;
uniform bool shadowSoft;
uniform float shadowTexel;
uniform float shadowDarkness;
uniform sampler2D shadowTex;

// Varying
varying float d;
varying vec3 N,topLightDir;
varying vec4 shadowCoord;

vec3 seaGradientColor(vec3 color){
	return mix(glowColor, color.rgb, clamp((visibleLimit-d)/400.0, 0.1, 1.0));
}

// 1.0 = fully lit, 0.0 = fully shadowed (PCF when the shadow map is soft)
float getShadow(){
	if (!shadowEnabled) return 1.0;
	if (shadowCoord.w == 0.0) return 1.0;
	vec3 proj = shadowCoord.xyz / shadowCoord.w;
	if (proj.x < -1.0 || proj.x > 1.0 || proj.y < -1.0 || proj.y > 1.0) return 1.0;
	if (proj.z > 1.0 || proj.z < -1.0) return 1.0;

	vec2 uv = proj.xy * 0.5 + 0.5;
	float depth = proj.z * 0.5 + 0.5;
	float bias = 0.0018;

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
	
	vec4 finalColor = vec4(0,0,0,0);
	vec4 color = gl_Color;
	if (useAlphaAsHeight) color.a = 1.0;
	

	if (directColor || emissive){
		finalColor = texture2D(colorTex, gl_TexCoord[0].st).rgba * color;
		
		// Gradient blur
		if (emissive) finalColor.a = min(finalColor.a,(visibleLimit-d)/40.0);
	}
	else{
		finalColor = texture2D(colorTex, gl_TexCoord[0].st).rgba * color;
		
		if (discardTransparency && finalColor.a==0.0){
			discard;
			return;
		}
		
		// ligh from top
		if (topLight){
			finalColor.rgb = finalColor.rgb * (dot(normalize(N),normalize(topLightDir)) / 1.5 + 1.0);
		}
			
		if (!highlight){
			finalColor = finalColor * vec4(light_ambient,1.0);
		}
		
		// Shadow from the light coming straight from above
		finalColor.rgb *= mix(1.0 - shadowDarkness, 1.0, getShadow());
		
		// Water Glow Color Interpolation
		finalColor.rgb = seaGradientColor(finalColor.rgb);
		
		// Emissive light from alpha
		if (alphaLightPercent>0.0){
			if (invertAlphaLight){
				finalColor.rgb=finalColor.rgb+finalColor.a*alphaLightPercent*alphaLightcolor;
			}
			else{
				finalColor.rgb=finalColor.rgb+(1.0-finalColor.a)*alphaLightPercent*alphaLightcolor;
				finalColor.a=1.0;
			}
		}
		
		// Selection
		if (selected) finalColor.rgb = finalColor.rgb * selectedFactor;
		
		// Gradient blur
		finalColor.a = min(finalColor.a,(visibleLimit-d)/40.0);
	}
	
	gl_FragColor = finalColor;
	
	
}