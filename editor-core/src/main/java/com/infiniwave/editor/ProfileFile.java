package com.infiniwave.editor;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.*;

/** Small strict JSON schema: no executable expressions, external references, or PCM data. */
public final class ProfileFile {
    public static final String FORMAT="infiniforge.synth-profile";
    public static final int VERSION=1,MAX_BYTES=131072;
    public static void write(RenderProfile profile,OutputStream output)throws IOException{
        StringBuilder json=new StringBuilder("{\n  \"format\": \""+FORMAT+"\",\n  \"version\": 1,\n  \"engine\": ");
        quote(json,profile.engine().name());json.append(",\n  \"parameters\": {");int i=0;
        for(var e:profile.parameters().entrySet()){
            if(i++>0)json.append(',');json.append("\n    ");quote(json,e.getKey());json.append(": ");
            // Numeric values use JSON number syntax; booleans remain booleans.
            String v=e.getValue();if(v.equals("true")||v.equals("false")||v.matches("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?"))json.append(v);else quote(json,v);
        }
        json.append("\n  }\n}\n");output.write(json.toString().getBytes(StandardCharsets.UTF_8));
    }
    public static RenderProfile read(InputStream input)throws IOException{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] block=new byte[4096];
        while(true){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Profile load cancelled");int n=input.read(block);if(n<0)break;if(n==0)throw new IOException("Profile stream made no progress");if(bytes.size()+n>MAX_BYTES)throw new IOException("Profile JSON exceeds 128 KiB");bytes.write(block,0,n);}
        try{
            String text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes.toByteArray())).toString();
            Parser p=new Parser(text);Map<String,Object> root=p.object(0);p.space();if(p.at!=text.length())throw new IOException("Trailing JSON data");
            if(root.size()!=4||!FORMAT.equals(root.get("format"))||!"1".equals(root.get("version"))||!(root.get("engine") instanceof String)||!(root.get("parameters") instanceof Map))throw new IOException("Unsupported synthesis profile schema");
            Map<String,String> params=new LinkedHashMap<>();for(var e:((Map<?,?>)root.get("parameters")).entrySet()){if(!(e.getValue() instanceof String))throw new IOException("Parameters must be numbers or booleans");params.put((String)e.getKey(),(String)e.getValue());}
            return new RenderProfile(RenderProfile.Engine.valueOf((String)root.get("engine")),params);
        }catch(IllegalArgumentException|CharacterCodingException e){throw new IOException("Invalid synthesis profile: "+e.getMessage(),e);}
    }
    private static void quote(StringBuilder out,String s){out.append('"');for(int i=0;i<s.length();i++){char c=s.charAt(i);if(c=='"'||c=='\\')out.append('\\').append(c);else if(c<32)out.append(String.format(Locale.ROOT,"\\u%04x",(int)c));else out.append(c);}out.append('"');}
    private static final class Parser {
        final String text;int at;
        Parser(String text){this.text=text;}
        void space(){while(at<text.length()&&" \t\r\n".indexOf(text.charAt(at))>=0)at++;}
        boolean take(char c){space();if(at<text.length()&&text.charAt(at)==c){at++;return true;}return false;}
        void need(char c)throws IOException{if(!take(c))throw new IOException("Expected JSON '"+c+"'");}
        Map<String,Object> object(int depth)throws IOException{
            if(depth>1)throw new IOException("Nested profile objects are unsupported");need('{');Map<String,Object> out=new LinkedHashMap<>();if(take('}'))return out;
            do{if(out.size()>=512)throw new IOException("Too many JSON fields");String key=string();need(':');space();Object value;
                if(at<text.length()&&text.charAt(at)=='{')value=object(depth+1);
                else if(at<text.length()&&text.charAt(at)=='"')value=string();
                else{int start=at;while(at<text.length()&&",} \t\r\n".indexOf(text.charAt(at))<0)at++;String token=text.substring(start,at);if(!token.equals("true")&&!token.equals("false")&&!token.matches("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?"))throw new IOException("Invalid JSON value");value=token;}
                if(out.put(key,value)!=null)throw new IOException("Duplicate JSON key: "+key);
            }while(take(','));need('}');return out;
        }
        String string()throws IOException{
            need('"');StringBuilder out=new StringBuilder();
            while(at<text.length()){
                char c=text.charAt(at++);if(c=='"')return out.toString();if(c<32)throw new IOException("Unescaped JSON control character");
                if(c=='\\'){
                    if(at==text.length())throw new IOException("Truncated JSON escape");char e=text.charAt(at++);
                    switch(e){case '"','\\','/'->c=e;case 'b'->c='\b';case 'f'->c='\f';case 'n'->c='\n';case 'r'->c='\r';case 't'->c='\t';case 'u'->{if(at+4>text.length())throw new IOException("Truncated Unicode escape");try{c=(char)Integer.parseInt(text.substring(at,at+4),16);}catch(NumberFormatException ex){throw new IOException("Invalid Unicode escape");}at+=4;}default->throw new IOException("Invalid JSON escape");}
                }
                out.append(c);if(out.length()>512)throw new IOException("JSON string too long");
            }throw new IOException("Unterminated JSON string");
        }
    }
}
