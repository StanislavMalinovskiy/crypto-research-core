package io.cryptoresearch.marketdata.infrastructure;

import java.util.HashSet;
import tools.jackson.databind.json.JsonMapper;

/** Selects a validated JSON value's original character span, never a reserialized tree. */
final class ExactCaptureJson {
	private final String text;
	private int at;
	private String found;
	private final String wanted;
	private final java.util.List<String> elements=new java.util.ArrayList<>();
	private ExactCaptureJson(String text,String wanted) { this.text=text; this.wanted=wanted; }
	static String value(String text,String path) {
		try {
			JsonMapper.builder().build().readTree(text);
			var scan=new ExactCaptureJson(text,path); scan.read(""); scan.space();
			if(scan.at!=text.length()) throw new IllegalArgumentException();
			return scan.found;
		} catch(RuntimeException malformed) { throw new IllegalStateException("MALFORMED_PROVIDER_JSON"); }
	}
	static java.util.List<String> elements(String text,String path) {
		try {
			if(!JsonMapper.builder().build().readTree(text).at(path).isArray()) throw new IllegalArgumentException();
			var scan=new ExactCaptureJson(text,path); scan.read(""); scan.space(); if(scan.at!=text.length()) throw new IllegalArgumentException();
			return java.util.List.copyOf(scan.elements);
		} catch(RuntimeException malformed) { throw new IllegalStateException("MALFORMED_PROVIDER_JSON"); }
	}
	private void read(String path) {
		space(); var start=at;
		if(text.charAt(at)=='{') {
			at++; space(); var keys=new HashSet<String>();
			if(text.charAt(at)!='}') do {
				space(); var begin=at; string();
				var key=JsonMapper.builder().build().readValue(text.substring(begin,at),String.class);
				if(!keys.add(key)) throw new IllegalArgumentException();
				space(); expect(':'); read(path+"/"+key.replace("~","~0").replace("/","~1")); space();
				if(text.charAt(at)!=',') break; at++;
			} while(true);
			expect('}');
		} else if(text.charAt(at)=='[') {
			at++; space(); var index=0;
			if(text.charAt(at)!=']') do {
				read(path+"/"+index++); space(); if(text.charAt(at)!=',') break; at++;
			} while(true);
			expect(']');
		} else if(text.charAt(at)=='"') string();
		else { while(at<text.length() && ",]} \t\r\n".indexOf(text.charAt(at))<0) at++; }
		if(path.equals(wanted)) found=text.substring(start,at);
		else if(path.startsWith(wanted+"/") && path.substring(wanted.length()+1).matches("[0-9]+")) elements.add(text.substring(start,at));
	}
	private void string() {
		expect('"');
		while(at<text.length()) { var ch=text.charAt(at++); if(ch=='"') return; if(ch=='\\') at++; }
		throw new IllegalArgumentException();
	}
	private void space() { while(at<text.length() && Character.isWhitespace(text.charAt(at))) at++; }
	private void expect(char ch) { if(text.charAt(at++)!=ch) throw new IllegalArgumentException(); }
}
