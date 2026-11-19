package com.infiniwave.editor;
import java.util.*;

/** Authoritative synthesis settings. Track PCM is a replaceable render cache. */
public record RenderProfile(Engine engine,Map<String,String> parameters) {
    public enum Engine { FM, OPL3, SID6581, CMS }
    public RenderProfile {
        Objects.requireNonNull(engine);Objects.requireNonNull(parameters);
        if(parameters.size()>512)throw new IllegalArgumentException("Too many profile parameters");
        Map<String,String> copy=new LinkedHashMap<>();
        for(var e:parameters.entrySet()){
            if(e.getKey()==null||!e.getKey().matches("[a-zA-Z][a-zA-Z0-9-]{0,63}")||e.getValue()==null||e.getValue().length()>128)throw new IllegalArgumentException("Invalid profile parameter");
            String v=e.getValue();
            if(!v.equals("true")&&!v.equals("false")){
                try{if(!Double.isFinite(Double.parseDouble(v)))throw new NumberFormatException();}catch(NumberFormatException ex){throw new IllegalArgumentException("Profile parameters must be finite numbers or booleans");}
            }
            copy.put(e.getKey(),v);
        }
        parameters=Collections.unmodifiableMap(copy);
        if(engine==Engine.FM||engine==Engine.OPL3){int n;try{n=Integer.parseInt(copy.get("operators"));}catch(Exception e){throw new IllegalArgumentException("Missing operator count");}
            if(engine==Engine.FM?(n!=1&&n!=2&&n!=4&&n!=6&&n!=8):(n!=2&&n!=4))throw new IllegalArgumentException("Unsupported operator count");}
    }
}
