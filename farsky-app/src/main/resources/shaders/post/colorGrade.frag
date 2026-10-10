#version 110

// Color grading: saturation, contrast and a subtle tint of the final image.
uniform sampler2D texture;
uniform float saturation;
uniform float contrast;
uniform vec3 tint;
uniform float strength;

void main(){
	vec4 texel = texture2D(texture, gl_TexCoord[0].st);
	vec3 color = texel.rgb;

	float luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
	vec3 graded = mix(vec3(luma), color, saturation);
	graded = (graded - 0.5) * contrast + 0.5;
	graded *= tint;
	graded = clamp(graded, 0.0, 1.0);

	gl_FragColor = vec4(mix(color, graded, strength), texel.a);
}
