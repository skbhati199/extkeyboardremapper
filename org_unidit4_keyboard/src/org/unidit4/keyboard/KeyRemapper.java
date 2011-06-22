package org.unidit4.keyboard;

import java.util.HashMap;
import android.util.Log;
//For load()
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.unidit4.keyboard.R;

public class KeyRemapper {
	private HashMap<Integer,Integer> scanCodeToKeyCode;
	private HashMap<Integer,Character[]> keyCodeToGlyphs;
	public static final String TAG = "myIMEremapHash";
	public static final int NORMAL_INDEX=0;
	public static final int SHIFT_INDEX=1;
	public static final int ALT_INDEX=2;
	public static final int SYM_INDEX=3;
	
	public KeyRemapper(){
		scanCodeToKeyCode = new HashMap<Integer,Integer>();
		keyCodeToGlyphs	= new HashMap<Integer,Character[]>();
		
	}
	public void clear(){
		scanCodeToKeyCode.clear();
		keyCodeToGlyphs.clear();
	}
	public void destroy(){
		clear();
		scanCodeToKeyCode=null;
		keyCodeToGlyphs=null;
	}
	
	public void addRemapScanCode(Integer scanCode,Integer keyCode){
		if ((keyCode < 0) || (scanCode <0)){
			//TODO throw or log here
			return;
		}

		scanCodeToKeyCode.put(scanCode,keyCode);
		Log.d(TAG,"added scan to key substitution "+scanCode+"->"+keyCode);
	}
	public void addRemapGlyph(Integer keyCode,Character glyphs[]){
		if (glyphs.length != 4) {
			Log.w(TAG,"Invalid glyph number " + glyphs.length);
			return;
		}
		if (keyCode < 0){
			Log.w(TAG,"Invalid keycode for glyphs "+keyCode);
			return;
		}
		keyCodeToGlyphs.put(keyCode,glyphs);
		Log.d(TAG,"added Glyph for "+keyCode+" ["+glyphs[0]+","+glyphs[1]+","+glyphs[2]+","+glyphs[3]+"]");
	}
	
	public Integer getKeyCode(Integer scanCode){
		return scanCodeToKeyCode.get(scanCode);
	}
	
	public Character[] getGlyphs(Integer keyCode){
		return keyCodeToGlyphs.get(keyCode);
	}

	public boolean loadScanToKey(String fullPath){
		//File format : <integer>[:=-> ]+<integer>
		Pattern keyValue=Pattern.compile("\\s*(\\d+)[:=\\-> \t]+(\\d+)");
		Matcher KVmatch;
		try {
		      //use buffering, reading one line at a time
		      //FileReader always assumes default encoding is OK!
		      BufferedReader input =  new BufferedReader(new FileReader(fullPath));
		      try {
		        String line = null; //not declared within while loop
		        /*
		        * readLine is a bit quirky :
		        * it returns the content of a line MINUS the newline.
		        * it returns null only for the END of the stream.
		        * it returns an empty String if two newlines appear in a row.
		        */
		        while (( line = input.readLine()) != null){
		        	KVmatch=keyValue.matcher(line);
		        	if(KVmatch.find()){
		        		Integer scanCode=Integer.parseInt(KVmatch.group(1));
		        		Integer keyCode=Integer.parseInt(KVmatch.group(2));
		        		addRemapScanCode(scanCode,keyCode);
		        		Log.d(TAG,"Added "+scanCode+"->"+keyCode+"substitution");
		        	}
		        	else {
		        		Log.d(TAG,"Skipping line : "+line);
		        	}
		        }
		      }
		      finally {
		        input.close();
		      }
		    }
		    catch (IOException ex){
		      Log.e(TAG,"Unhandled error : "+ex);
		      return false;
		    }
		return true;
	}
	public boolean loadGlyphs(String fullPath){
		//File format : <integer> <Glyph> <Glyph> <Glyph> <Glyph>
		Pattern Gpattern=Pattern.compile("\\s*(\\d+)\\s+(\\S+)\\s+(\\S+)\\s+(\\S+)\\s+(\\S+)");
		Matcher Gmatch;
		try {
		      //use buffering, reading one line at a time
		      //FileReader always assumes default encoding is OK!
		      BufferedReader input =  new BufferedReader(new FileReader(fullPath));
		      try {
		        String line = null; //not declared within while loop
		        /*
		        * readLine is a bit quirky :
		        * it returns the content of a line MINUS the newline.
		        * it returns null only for the END of the stream.
		        * it returns an empty String if two newlines appear in a row.
		        */
		        while (( line = input.readLine()) != null){
		        	Gmatch=Gpattern.matcher(line);
		        	if(Gmatch.find()){
		        		Integer kc=Integer.parseInt(Gmatch.group(1));
		        		
		        		Character Glyphs[]=new Character[]{null,null,null,null};
		        		boolean allOk=true;
		        		for(int i=2;i<6;i++) {
		        			String raw=Gmatch.group(i);
		        			if (raw.length()==1){
		        				// simply a char
		        				Glyphs[i-2]=raw.charAt(0);
		        			}
		        			else if (raw.length()==4){
		        				// try to read this as a hexadecimal string
		        				try{
		        					Glyphs[i-2]=new Character((char)Integer.parseInt(raw, 16));
		        				}
		        				catch(Exception e){
		        					Log.w(TAG,"Error while parsing "+raw+" as hexadecimal");
		        					allOk=false;
		        				}
		        			}
		        			else {
		        				//Neither 1 char nor 4 chars : an error
		        				Log.w(TAG,"Error : ("+raw+") is not a valid glyph");
		        				allOk=false;
		        			}
		        		}
		        		
		        		if (allOk){
		        			addRemapGlyph(kc,Glyphs);
		        			
		        		}
		        	}
		        	else {
		        		Log.d(TAG,"Skipping line : "+line);
		        	}
		        }
		      }
		      finally {
		        input.close();
		      }
		    }
		    catch (IOException ex){
		      Log.d(TAG,"Unhandled error : "+ex);
		      return false;
		    }
		return true;
	}

}
