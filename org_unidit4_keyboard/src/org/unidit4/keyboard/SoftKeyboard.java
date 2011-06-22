/*
 * Copyright (C) 2008-2009 Google Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

/*
 * Contains large modifications from laurent2o1o to support external keyboard remapping
 */

package org.unidit4.keyboard;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.inputmethodservice.InputMethodService;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.text.method.MetaKeyKeyListener;
import android.util.Log;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.CompletionInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;

import java.util.ArrayList;
import java.util.List;

import org.unidit4.keyboard.R;

import android.widget.ImageView;
import android.widget.Toast;
/**
 * Example of writing an input method for a soft keyboard. This code is focused
 * on simplicity over completeness, so it should in no way be considered to be a
 * complete soft keyboard implementation. Its purpose is to provide a basic
 * example for how you would get started writing an input method, to be fleshed
 * out as appropriate.
 */
public class SoftKeyboard extends InputMethodService implements
		KeyboardView.OnKeyboardActionListener {
	static final boolean DEBUG = true;
	private static final String TAG = "myIME";
	private static int TOAST_DELAY=1000; // minimum millisecond between toasts
	private boolean mDisplaySoftKeyboardAlways;
	private Toast mToaster;
	private AlertDialog mOptionsDialog;
	private KeyRemapper mKeyMap ;
	private long mLastToast;
	/**
	 * This boolean indicates the optional example code for performing
	 * processing of hard keys in addition to regular text generation from
	 * on-screen interaction. It would be used for input methods that perform
	 * language translations (such as converting text entered on a QWERTY
	 * keyboard to Chinese), but may not be used for input methods that are
	 * primarily intended to be used for on-screen text entry.
	 */
	static final boolean PROCESS_HARD_KEYS = true;

	private KeyboardView mInputView;
	//private CandidateView mCandidateView;
	private CompletionInfo[] mCompletions;

	private StringBuilder mComposing = new StringBuilder();
	private boolean mPredictionOn;
	private boolean mCompletionOn;
	// added for bt
	private boolean mRemapForBT;
	private boolean mToastOnKeys;
	// added
	private int mLastDisplayWidth;
	private boolean mCapsLock;
	private long mLastShiftTime;
	private long mMetaState;

	private LatinKeyboard mSymbolsKeyboard;
	private LatinKeyboard mSymbolsShiftedKeyboard;
	private LatinKeyboard mQwertyKeyboard;

	private LatinKeyboard mCurKeyboard;

	private String mWordSeparators;

	//TODO Remove me! Force softinput in emulator/landscape
	@Override 
	public boolean onEvaluateInputViewShown(){
		return true;
	}
	@Override 
	public boolean onEvaluateFullscreenMode(){
		return false;
	}
	
	/**
	 * Main initialization of the input method component. Be sure to call to
	 * super class.
	 */
	@Override
	public void onCreate() {
		mInputView=null;
		mQwertyKeyboard=null;
		mSymbolsKeyboard=null;
		mSymbolsShiftedKeyboard=null;
		mCurKeyboard=null;
 
		super.onCreate();
		mWordSeparators = getResources().getString(R.string.word_separators);
		Log.d(TAG, "onCreate");
		mRemapForBT = true;
		mDisplaySoftKeyboardAlways=false;
		mToastOnKeys=false;
		if (mKeyMap != null) {
			mKeyMap.clear();
		}

		mKeyMap = new KeyRemapper();
		Log.d(TAG, "Loading ScanCodes to keyCodes");
		mKeyMap.loadScanToKey("/sdcard/keyremap/s2k.cfg");
		Log.d(TAG, "Loading Glyph substitution");
		mKeyMap.loadGlyphs("/sdcard/keyremap/k2g.cfg");
	}
	
	@Override
	public void onDestroy(){
		Log.d(TAG,"onDestroy: Goodbye Cruel World");
		mKeyMap.destroy();
		mKeyMap=null;
		super.onDestroy();
	}

	/**
	 * This is the point where you can do all of your UI initialization. It is
	 * called after creation and any configuration change.
	 */
	@Override
	public void onInitializeInterface() {
		Log.i(TAG,"onInitialise interface. Context:"+this+" AppContext:"+getApplicationContext());
		if (mQwertyKeyboard != null) {
			// Configuration changes can happen after the keyboard gets
			// recreated,
			// so we need to be able to re-build the keyboards if the available
			// space has changed.
			Log.d(TAG, "Changing keyboards if necessary");
			int displayWidth = getMaxWidth();
			// we return ONLY if the width has changed
			if (displayWidth == mLastDisplayWidth) return;
			// Still there ? we need to adapt to the new width
			mLastDisplayWidth = displayWidth;
		}
		// I dislike to give "this" as a context => memory leaks
		mQwertyKeyboard = new LatinKeyboard(this, R.xml.qwerty);
		mSymbolsKeyboard = new LatinKeyboard(this, R.xml.symbols);
		mSymbolsShiftedKeyboard = new LatinKeyboard(this, R.xml.symbols_shift);

	}

	/**
	 * Called by the framework when your view for creating input needs to be
	 * generated. This will be called the first time your input method is
	 * displayed, and every time it needs to be re-created such as due to a
	 * configuration change.
	 */
	@Override
	public View onCreateInputView() {
		Log.d(TAG, "onCreateInputView");
		// First we check that it does not exist
		// if it does, we unset the keyboardactionlistener to avoid that the previous
		// context leaks memory in the input view
		if (mInputView != null) {
			//cross fingers
//			Log.d(TAG,"Uninstalling keboard and key callback for InputView");
//			mInputView.setOnKeyboardActionListener(null);
//			Log.d(TAG,"ActionListener uninstalled");
//			mInputView.setKeyboard(null);
//			Log.d(TAG,"Keyboard Uninstalled");
			mInputView=null;
		}
		// Creating view then installing keyboard
		mInputView = (KeyboardView) getLayoutInflater().inflate(R.layout.input,
				null);
		mInputView.setOnKeyboardActionListener(this);
		mInputView.setKeyboard(mQwertyKeyboard) ;
		return mInputView;
	}

	/**
	 * Called by the framework when your view for showing candidates needs to be
	 * generated, like {@link #onCreateInputView}.
	 */
	@Override
	public View onCreateCandidatesView() {
//		Log.d(TAG, "onCreateCandidateView");
//		mCandidateView = new CandidateView(this);
//		mCandidateView.setService(this);
//		return mCandidateView;
		return null; // we do not want this yet!
	}

	/**
	 * This is the main point where we do our initialization of the input method
	 * to begin operating on an application. At this point we have been bound to
	 * the client, and are now receiving all of the detailed information about
	 * the target of our edits.
	 */
	@Override
	public void onStartInput(EditorInfo attribute, boolean restarting) {
		super.onStartInput(attribute, restarting);
		Log.d(TAG, "onStartInput");
		// Reset our state. We want to do this even if restarting, because
		// the underlying state of the text editor could have changed in any
		// way.
		mComposing.setLength(0);
		Log.d(TAG,"onStartInput updating candidates");
		updateCandidates();
		Log.d(TAG,"onStartInput updating candidates ... done");

		if (!restarting) {
			// Clear shift states.
			mMetaState = 0;
		}

		mPredictionOn = false;
		mCompletionOn = false;
		mCompletions = null;
		// We are now going to initialize our state based on the type of
		// text being edited.
		switch (attribute.inputType & EditorInfo.TYPE_MASK_CLASS) {
		case EditorInfo.TYPE_CLASS_NUMBER:
		case EditorInfo.TYPE_CLASS_DATETIME:
			// Numbers and dates default to the symbols keyboard, with
			// no extra features.
			mCurKeyboard = mSymbolsKeyboard;
			break;

		case EditorInfo.TYPE_CLASS_PHONE:
			// Phones will also default to the symbols keyboard, though
			// often you will want to have a dedicated phone keyboard.
			mCurKeyboard = mSymbolsKeyboard;
			break;

		case EditorInfo.TYPE_CLASS_TEXT:
			// This is general text editing. We will default to the
			// normal alphabetic keyboard, and assume that we should
			// be doing predictive text (showing candidates as the
			// user types).
			mCurKeyboard = mQwertyKeyboard;
			// mPredictionOn = true;
			mPredictionOn = false; // CHANGED

			// We now look for a few special variations of text that will
			// modify our behavior.
			int variation = attribute.inputType
					& EditorInfo.TYPE_MASK_VARIATION;
			if (variation == EditorInfo.TYPE_TEXT_VARIATION_PASSWORD
					|| variation == EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD) {
				// Do not display predictions / what the user is typing
				// when they are entering a password.
				mPredictionOn = false;
			}

			if (variation == EditorInfo.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
					|| variation == EditorInfo.TYPE_TEXT_VARIATION_URI
					|| variation == EditorInfo.TYPE_TEXT_VARIATION_FILTER) {
				// Our predictions are not useful for e-mail addresses
				// or URIs.
				mPredictionOn = false;
			}

			if ((attribute.inputType & EditorInfo.TYPE_TEXT_FLAG_AUTO_COMPLETE) != 0) {
				// If this is an auto-complete text view, then our predictions
				// will not be shown and instead we will allow the editor
				// to supply their own. We only show the editor's
				// candidates when in fullscreen mode, otherwise relying
				// own it displaying its own UI.
				mPredictionOn = false;
				mCompletionOn = isFullscreenMode();
			}

			// We also want to look at the current state of the editor
			// to decide whether our alphabetic keyboard should start out
			// shifted.
			updateShiftKeyState(attribute);
			break;

		default:
			// For all unknown input types, default to the alphabetic
			// keyboard with no special features.
			mCurKeyboard = mQwertyKeyboard;
			updateShiftKeyState(attribute);
		}

		// Update the label on the enter key, depending on what the application
		// says it will do.
			mCurKeyboard.setImeOptions(getResources(), attribute.imeOptions);
	}

	/**
	 * This is called when the user is done editing a field. We can use this to
	 * reset our state.
	 */
	@Override
	public void onFinishInput() {
		super.onFinishInput();
		Log.d(TAG, "onFinishInput");
		// Clear current composing text and candidates.
		mComposing.setLength(0);
		updateCandidates();

		// We only hide the candidates window when finishing input on
		// a particular editor, to avoid popping the underlying application
		// up and down if the user is entering text into the bottom of
		// its window.
		setCandidatesViewShown(false);

		mCurKeyboard = mQwertyKeyboard;
		if (mInputView != null) {
			mInputView.closing();
		}
	}

	@Override
	public void onStartInputView(EditorInfo attribute, boolean restarting) {
		Log.d(TAG,"onStartInputView with params "+attribute.toString()+" restarting :"+restarting);
		super.onStartInputView(attribute, restarting);
		Log.d(TAG, "onStartInputView just called super.onStartInputView");
		// Apply the selected keyboard to the input view.
		mInputView.setKeyboard(mCurKeyboard);
		Log.d(TAG,"just after setKeyboard");
		if (mInputView.isShown()) {
			
			mInputView.closing();
		}
		Log.d(TAG,"Quitting onStartInputView");
	}

	/**
	 * Deal with the editor reporting movement of its cursor.
	 */
	@Override
	public void onUpdateSelection(int oldSelStart, int oldSelEnd,
			int newSelStart, int newSelEnd, int candidatesStart,
			int candidatesEnd) {
		Log.d(TAG, "onUpdateSelection");
		super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd,
				candidatesStart, candidatesEnd);

		// If the current selection in the text view changes, we should
		// clear whatever candidate text we have.
		if (mComposing.length() > 0
				&& (newSelStart != candidatesEnd || newSelEnd != candidatesEnd)) {
			mComposing.setLength(0);
			updateCandidates();
			InputConnection ic = getCurrentInputConnection();
			if (ic != null) {
				ic.finishComposingText();
			}
		}
	}

	/**
	 * This tells us about completions that the editor has determined based on
	 * the current text in it. We want to use this in fullscreen mode to show
	 * the completions ourself, since the editor can not be seen in that
	 * situation.
	 */
	@Override
	public void onDisplayCompletions(CompletionInfo[] completions) {
		if (mCompletionOn) {
			mCompletions = completions;
			if (completions == null) {
				setSuggestions(null, false, false);
				return;
			}

			List<String> stringList = new ArrayList<String>();
			for (int i = 0; i < (completions != null ? completions.length : 0); i++) {
				CompletionInfo ci = completions[i];
				if (ci != null)
					stringList.add(ci.getText().toString());
			}
			setSuggestions(stringList, true, true);
		}
	}

	/**
	 * This translates incoming hard key events in to edit operations on an
	 * InputConnection. It is only needed when using the PROCESS_HARD_KEYS
	 * option.
	 */
	private boolean translateKeyDown(int keyCode, KeyEvent event) {
		Log.d(TAG, "translateKeyDown " + event);
		mMetaState = MetaKeyKeyListener.handleKeyDown(mMetaState, keyCode,
				event);
		int c = event.getUnicodeChar(MetaKeyKeyListener
				.getMetaState(mMetaState));
		mMetaState = MetaKeyKeyListener.adjustMetaAfterKeypress(mMetaState);
		InputConnection ic = getCurrentInputConnection();
		if (c == 0 || ic == null) {
			return false;
		}

		boolean dead = false;

		if ((c & KeyCharacterMap.COMBINING_ACCENT) != 0) {
			dead = true;
			c = c & KeyCharacterMap.COMBINING_ACCENT_MASK;
		}

		if (mComposing.length() > 0) {
			char accent = mComposing.charAt(mComposing.length() - 1);
			int composed = KeyEvent.getDeadChar(accent, c);

			if (composed != 0) {
				c = composed;
				mComposing.setLength(mComposing.length() - 1);
			}
		}

		onKey(c, null);

		return true;
	}
	
    private void showOptionsMenu() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        if (builder == null){
        	Log.d(TAG,"Unable to retrieve builder!");
        	return;
        }
        builder.setCancelable(true);
        builder.setIcon(R.drawable.unset);
        builder.setNegativeButton(android.R.string.cancel, null);
        CharSequence itemSettings = getString(R.string.ime_settings);
        CharSequence itemInputMethod = getString(R.string.inputMethod);
        CharSequence itemToastOnKey = (mToastOnKeys?getString(R.string.disableToastOnKey):getString(R.string.enableToastOnKey));
        builder.setItems(new CharSequence[] {
                itemSettings, itemInputMethod, itemToastOnKey},
                new DialogInterface.OnClickListener() {

            public void onClick(DialogInterface di, int position) {
                di.dismiss();
                switch (position) {
                    case 0:
                       // launchSettings();
                    	Log.d(TAG,"LaunchSettings not implemented yet");
                    	//mToaster =Toast.makeText(this, "Not implemented yet",Toast.LENGTH_LONG);
                        break;
                    case 1:
                        InputMethodManager imManager = (InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
                        imManager.showInputMethodPicker();
                        break;
                    case 2:
                    	mToastOnKeys = !mToastOnKeys;
                    	break;
                }
            }
        });
        builder.setTitle(getResources().getString(R.string.ime_name));
 
        mOptionsDialog = builder.create();
        Log.d(TAG,"Builder created");
        Window window = mOptionsDialog.getWindow();
        
        WindowManager.LayoutParams lp = window.getAttributes();
        lp.token = mInputView.getWindowToken();
        lp.type = WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG;
        window.setAttributes(lp);
        window.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
        Log.d(TAG,"Showing Window");
        mOptionsDialog.show();
        Log.d(TAG,"... done");
    }
    
	/**
	 * Use this to monitor key events being delivered to the application. We get
	 * first crack at them, and can either resume them or let them continue to
	 * the app.
	 */
	@Override
	public boolean  onKeyDown(int keyCode, KeyEvent event) {
		int devId = event.getDeviceId();
		Log.w(TAG, "KEYDOWN keycode:" + keyCode + " event:" + event
				+ " From device : " + devId);
		
		if(mToastOnKeys) {
			// do not retoast too fast
			if (mLastToast < event.getEventTime() - TOAST_DELAY){
			//Context context = getApplicationContext();
			CharSequence text = "Physical (Scancode): "+event.getScanCode()+"\nLogical (Keycode): "+event.getKeyCode() + " '"+event.getDisplayLabel() + "' key";
			int duration = Toast.LENGTH_SHORT;
			mToaster = Toast.makeText(this, text, duration);
			if(mToaster != null) mToaster.show();
			}
			mLastToast=event.getEventTime();
		}

		if (devId > 0) {
			// External keyboard : get the property
			// here, we could substitute mappings!

		} else if (devId < 0) {
			// This is onscreen keyboard, we do not want to treat this
			Log.d(TAG, "Don't want to remap key for this device");
			if (keyCode==KeyEvent.KEYCODE_HOME) {
				Log.d(TAG,"This was the HOME key, returning false...");
				return false;
			}
			return super.onKeyDown(keyCode, event); // or false ?
		}
		

//		ImageView icone= (ImageView) mInputView.findViewById(R.id.kbdIcon);
//		if (icone == null){Log.e(TAG,"didn't find icon");} else {icone.setImageResource(R.drawable.unset);}
		switch (keyCode) {
		case KeyEvent.KEYCODE_SEARCH:
		case KeyEvent.KEYCODE_HOME:
			// We never handle these
			Log.d(TAG,"Home or search was called: return false");
			return false; 
			
		case KeyEvent.KEYCODE_BACK:
			// The InputMethodService already takes care of the back
			// key for us, to dismiss the input method if it is shown.
			// However, our keyboard could be showing a pop-up window
			// that back should dismiss, so we first allow it to do that.
			Log.d(TAG,"Back was called");
			if (event.getRepeatCount() == 0 && mInputView != null) {
				//TODO handle dialog if any ?
				Log.d(TAG,"input view not null, handling back");
				if (mInputView.handleBack()) {
					Log.d(TAG,"Returning true");
					return true;
				}
				//WARNING! if it's archos softkey it just crashes on return super.onKeydown(keyCode,event)
				//UPDATE : Found a bug in GoogleSearch widget / Archos SoftKeyboard /Bluetooth keyboard
				// When a keyboard is connected in BT we can crash archos by bringing
				// googlesearch to front, then home (Archos soft keyboard) then Googlesearch, ... 4 times
				Log.d(TAG,"inputview==null or event.repeat != 0 , returning false");
				return false; // we do not want this
			}
			break;

		case KeyEvent.KEYCODE_DEL:
			// Special handling of the delete key: if we currently are
			// composing text for the user, we want to modify that instead
			// of let the application to the delete itself.
			if (mComposing.length() > 0) {
				onKey(Keyboard.KEYCODE_DELETE, null);
				return true;
			}
			break;

		case KeyEvent.KEYCODE_ENTER:
			// Let the underlying text editor always handle these.
			return false;

		default:
			// For all other keys, if we want to do transformations on
			// text being entered with a hard keyboard, we need to process
			// it and do the appropriate action.
			if (PROCESS_HARD_KEYS) {
				Log.d(TAG, "HardKey Processing");
				
				// Magic keys : toggle remapping by alt+web
				if ((keyCode == 0) && (event.getScanCode() == 172)) {
					mRemapForBT = !mRemapForBT;
					Log.e(TAG, "Remapping is now " + mRemapForBT);
					return true; // let it pass through
				}//special key

				// Force soft keyboard display with alt+enter
//				if ((keyCode==KeyEvent.KEYCODE_SPACE)&&(event.isShiftPressed())){
//					mDisplaySoftKeyboardAlways=! mDisplaySoftKeyboardAlways;
//					Log.e(TAG,"DisplaySoftKeyboardAlways is now " +mDisplaySoftKeyboardAlways);
////					if(mDisplaySoftKeyboardAlways) showWindow(true);
////					else hideWindow();
//					//TODO cancel shift state
//					return true;
//				}
				// RAW REMAP of my bluetooth keyboard
				if (mRemapForBT) {
					Log.d(TAG, "Remapping...");
					// Fetch a keycode substitution if any
					if (mKeyMap == null) {
						Log.e(TAG, "keymap is dead!");
						return false;
					}
					Integer newKeyCode = mKeyMap.getKeyCode(event.getScanCode());
					if (newKeyCode != null) {
						// Substitution exists
						Log.d(TAG, event.getScanCode() + "=> " + newKeyCode);
						keyCode = newKeyCode;
					}

					// Fetch Glyphs for this keycode if any
					Character glyphs[] = mKeyMap.getGlyphs(keyCode);
					if (glyphs != null) {
						// There are glyphs
						InputConnection ic = getCurrentInputConnection();
						if (ic != null) {
							Log.d(TAG,
									"Glyph substitution with input connection alive");
							int metaIndex = 0;
							int metastate = event.getMetaState();
							// find index meta from metaState
							if ((metastate & KeyEvent.META_ALT_ON) != 0) {
								metaIndex = KeyRemapper.ALT_INDEX;
								ic.clearMetaKeyStates(KeyEvent.META_ALT_ON);
							} else if ((metastate & KeyEvent.META_SHIFT_ON) != 0) {
								metaIndex = KeyRemapper.SHIFT_INDEX;
								ic.clearMetaKeyStates(KeyEvent.META_SHIFT_ON);
							} else if ((metastate & KeyEvent.META_SYM_ON) != 0) {
								metaIndex = KeyRemapper.SYM_INDEX;
								ic.clearMetaKeyStates(KeyEvent.META_SYM_ON);
							} else {
								metaIndex = KeyRemapper.NORMAL_INDEX;
							}
							char UTF16_char=glyphs[metaIndex];
							
							//First, we try to translate this glyph into key events
							//retrieve the keycharacter map from the event
							//TODO this is ineffective : cache it ?
							KeyCharacterMap myMap=KeyCharacterMap.load(event.getDeviceId());
							KeyEvent evs[]=null;
							char chars[]={' '};
							chars[0]=UTF16_char;
							// retrieve the key events that could have produced this glyph
							evs=myMap.getEvents(chars);
							
							if (evs != null){
								// we can reproduce this!
								Log.d(TAG,"sending events that can reproduce glyph");
								for (int i=0; i< evs.length;i++) ic.sendKeyEvent(evs[i]);
								return true;
							}
							// If we are still there, events could not be found to reproduce the glyph
							// just try to write the glyph directly
							
							
							// We first want to see if connected output can handle glyphs
							EditorInfo ei=getCurrentInputEditorInfo();

							// If editor info is invalid, return
							if (ei==null) return false;
							
							if (ei.inputType != EditorInfo.TYPE_NULL) {
								// the input connection can handle characters
								// just send the char
								ic.commitText(String.valueOf((char) UTF16_char), 1);
								Log.d(TAG, "Sending substituted glyph as a character ['"
										+ String.valueOf((char) UTF16_char) + "'="
										+ Integer.toHexString((int)(UTF16_char))
										+"] index:" + metaIndex);
								return true;
							} else {
								// Glyph substitution but the connection does
								// not handle chars, let it continue
								Log.d(TAG,"Glyphs but receiver does not handle glyphs, continuing");
								// Let the keycode continue its life
							}// Editor input type
						}//ic connection != null

					}//if Glyph Substitution
					
					if (newKeyCode == null) {
						Log.d(TAG, "This key is not remapped backing on super.onKeyDown");
						return super.onKeyDown(keyCode,event);
					}

					// remaps are okay here
					// send Down/Up
					if ((newKeyCode != null) && (glyphs == null)) {
						// new keycode without glyph substitution
						// send up/down key
						// we changed something, let's transmit this
						// Build a new event with the substituted keycode
						KeyEvent nk = new KeyEvent(event.getDownTime(),
								event.getEventTime(), event.getAction(),
								keyCode, event.getRepeatCount(),
								event.getMetaState(), event.getDeviceId(),
								event.getScanCode(), event.getFlags());
						Log.d(TAG,
								"keycode subst with no glyph subst : Send keyDown/Up to Connection "
										+ nk);
						InputConnection ic = getCurrentInputConnection();
						if (ic != null) {
							// send the event
							ic.sendKeyEvent(nk);
							// keyup
							ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,
									keyCode));
							return true;
						} else {
							Log.d(TAG,
									"Input connection dead, backing on super.onKeyDown");
							return super.onKeyDown(keyCode, nk);
						}//ic!=null
					}//if newKeyCode

				}// if mRemapBT

				// if (mPredictionOn && translateKeyDown(keyCode, event)) {
				// return true;
				// }
			}
		}//switch keycode
		Log.d(TAG, "not handled until the end, backing on super.onKeyDown");
		return super.onKeyDown(keyCode, event);
	}

	/**
	 * Use this to monitor key events being delivered to the application. We get
	 * first crack at them, and can either resume them or let them continue to
	 * the app.
	 */
	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		// We do not want to treat Archos onscreen keyboard
		if (event.getDeviceId() < 0)
			return super.onKeyUp(keyCode, event);
		// If we want to do transformations on text being entered with a hard
		// keyboard, we need to process the up events to update the meta key
		// state we are tracking.
		if (PROCESS_HARD_KEYS) {
			if (mPredictionOn) {
				mMetaState = MetaKeyKeyListener.handleKeyUp(mMetaState,
						keyCode, event);
			}
		}
//		ImageView icone= (ImageView) mInputView.findViewById(R.id.kbdIcon);
//		if (icone == null){Log.e(TAG,"didn't find icon");} else {icone.setImageResource(R.drawable.unset);}
		return super.onKeyUp(keyCode, event);
	}

	/**
	 * Helper function to commit any text being composed in to the editor.
	 */
	private void commitTyped(InputConnection inputConnection) {
		if (mComposing.length() > 0) {
			inputConnection.commitText(mComposing, mComposing.length());
			mComposing.setLength(0);
			updateCandidates();
		}
	}

	/**
	 * Helper to update the shift state of our keyboard based on the initial
	 * editor state.
	 */
	private void updateShiftKeyState(EditorInfo attr) {
		Log.d(TAG,"updateKeyShiftState");
		if (attr != null && mInputView != null
				&& mQwertyKeyboard == mInputView.getKeyboard()) {
			int caps = 0;
			EditorInfo ei = getCurrentInputEditorInfo();
			if (ei != null && ei.inputType != EditorInfo.TYPE_NULL) {
				caps = getCurrentInputConnection().getCursorCapsMode(
						attr.inputType);
			}
			boolean changed=mInputView.setShifted(mCapsLock || caps != 0);
			Log.d(TAG,"setShifted(mCapsLock("
					+mCapsLock
					+")||editorCaps("
					+(caps!=0)
					+") "
					+(changed?"changed":"did not change"));
		}
	}

	/**
	 * Helper to determine if a given character code is alphabetic.
	 */
	private boolean isAlphabet(int code) {
		if (Character.isLetter(code)) {
			return true;
		} else {
			return false;
		}
	}

	/**
	 * Helper to send a key down / key up pair to the current editor.
	 */
	private void keyDownUp(int keyEventCode) {
		Log.d(TAG,"keyDownUp event:"+keyEventCode);
		getCurrentInputConnection().sendKeyEvent(
				new KeyEvent(KeyEvent.ACTION_DOWN, keyEventCode));
		getCurrentInputConnection().sendKeyEvent(
				new KeyEvent(KeyEvent.ACTION_UP, keyEventCode));
	}

	/**
	 * Helper to send a character to the editor as raw key events.
	 */
	private void sendKey(int keyCode) {
		Log.d(TAG,"sendKey "+keyCode);
		switch (keyCode) {
		case '\n':
			keyDownUp(KeyEvent.KEYCODE_ENTER);
			break;
		default:
			if (keyCode >= '0' && keyCode <= '9') {
				keyDownUp(keyCode - '0' + KeyEvent.KEYCODE_0);
			} else {
				getCurrentInputConnection().commitText(
						String.valueOf((char) keyCode), 1);
			}
			break;
		}
	}

	// Implementation of KeyboardViewListener

	public void onKey(int primaryCode, int[] keyCodes) {
		Log.d(TAG,"onKey for code "+primaryCode);
		switch(primaryCode){
		case KeyEvent.KEYCODE_DPAD_DOWN:
		case KeyEvent.KEYCODE_DPAD_UP:
		case KeyEvent.KEYCODE_DPAD_LEFT:
		case KeyEvent.KEYCODE_DPAD_RIGHT:
			//transmit dpad events        
			//TODO also transmit the shift state of the soft keyboard?
            keyDownUp(primaryCode);
 			return;
		}
		
		if (isWordSeparator(primaryCode)) {
			// Handle separator
			if (mComposing.length() > 0) {
				commitTyped(getCurrentInputConnection());
			}
			sendKey(primaryCode);
			updateShiftKeyState(getCurrentInputEditorInfo());
		} else if (primaryCode == Keyboard.KEYCODE_DELETE) {
			handleBackspace();
		} else if (primaryCode == Keyboard.KEYCODE_SHIFT) {
			handleShift();
		} else if (primaryCode == Keyboard.KEYCODE_CANCEL) {
			handleClose();
			return;
		} else if (primaryCode == LatinKeyboardView.KEYCODE_OPTIONS) {
			Log.d(TAG,"Showing option menu");
			showOptionsMenu();
			

		} else if (primaryCode == Keyboard.KEYCODE_MODE_CHANGE
				&& mInputView != null) {
			Keyboard current = mInputView.getKeyboard();
			if (current == mSymbolsKeyboard
					|| current == mSymbolsShiftedKeyboard) {
				current = mQwertyKeyboard;
			} else {
				current = mSymbolsKeyboard;
			}
			mInputView.setKeyboard(current);
			if (current == mSymbolsKeyboard) {
				current.setShifted(false);
			}
		} else {
			handleCharacter(primaryCode, keyCodes);
		}
	}

	public void onText(CharSequence text) {
		Log.d(TAG, "onText : " + text);
		InputConnection ic = getCurrentInputConnection();
		if (ic == null)
			return;
		ic.beginBatchEdit();
		if (mComposing.length() > 0) {
			commitTyped(ic);
		}
		ic.commitText(text, 0);
		ic.endBatchEdit();
		updateShiftKeyState(getCurrentInputEditorInfo());
	}

	/**
	 * Update the list of available candidates from the current composing text.
	 * This will need to be filled in by however you are determining candidates.
	 */
	private void updateCandidates() {
		Log.d(TAG,"updateCandidates");

		if (!mCompletionOn) {
			if (mComposing.length() > 0) {
				ArrayList<String> list = new ArrayList<String>();
				list.add(mComposing.toString());
				list.add("Vache");
				setSuggestions(list, true, true);
			} else {
				setSuggestions(null, false, false);
			}
		}
	}

	public void setSuggestions(List<String> suggestions, boolean completions,
			boolean typedWordValid) {
		Log.d(TAG,"setSuggestions");

		if (suggestions != null && suggestions.size() > 0) {
			setCandidatesViewShown(true);
		} else if (isExtractViewShown()) {
			setCandidatesViewShown(true);
		}
//		if (mCandidateView != null) {
//			mCandidateView.setSuggestions(suggestions, completions,
//					typedWordValid);
//		}
	}

	private void handleBackspace() {
		Log.d(TAG,"handleBackspace");

		final int length = mComposing.length();
		if (length > 1) {
			mComposing.delete(length - 1, length);
			getCurrentInputConnection().setComposingText(mComposing, 1);
			updateCandidates();
		} else if (length > 0) {
			mComposing.setLength(0);
			getCurrentInputConnection().commitText("", 0);
			updateCandidates();
		} else {
			keyDownUp(KeyEvent.KEYCODE_DEL);
		}
		updateShiftKeyState(getCurrentInputEditorInfo());
	}

	private void handleShift() {
		Log.d(TAG,"handleShift");
		if (mInputView == null) {
			return;
		}

		Keyboard currentKeyboard = mInputView.getKeyboard();
		if (mQwertyKeyboard == currentKeyboard) {
			// Alphabet keyboard
			checkToggleCapsLock();
			mInputView.setShifted(mCapsLock || !mInputView.isShifted());
		} else if (currentKeyboard == mSymbolsKeyboard) {
			mSymbolsKeyboard.setShifted(true);
			mInputView.setKeyboard(mSymbolsShiftedKeyboard);
			mSymbolsShiftedKeyboard.setShifted(true);
		} else if (currentKeyboard == mSymbolsShiftedKeyboard) {
			mSymbolsShiftedKeyboard.setShifted(false);
			mInputView.setKeyboard(mSymbolsKeyboard);
			mSymbolsKeyboard.setShifted(false);
		}
	}

	private void handleCharacter(int primaryCode, int[] keyCodes) {
		Log.d(TAG,"handleCharacter");


		if (isInputViewShown()) {
			if (mInputView.isShifted()) {
				primaryCode = Character.toUpperCase(primaryCode);
			}
		}
		if (isAlphabet(primaryCode) && mPredictionOn) {
			mComposing.append((char) primaryCode);
			getCurrentInputConnection().setComposingText(mComposing, 1);
			updateShiftKeyState(getCurrentInputEditorInfo());
			updateCandidates();
		} else {
			getCurrentInputConnection().commitText(
					String.valueOf((char) primaryCode), 1);
			//This solves issue #5
			updateShiftKeyState(getCurrentInputEditorInfo());
			//
		} 
	}

	private void handleClose() {
		Log.d(TAG,"handleClose");


		commitTyped(getCurrentInputConnection());
		requestHideSelf(0);
		mInputView.closing();
	}

	private void checkToggleCapsLock() {
		long now = System.currentTimeMillis();
		if (mLastShiftTime + 800 > now) {
			mCapsLock = !mCapsLock;
			mLastShiftTime = 0;
		} else {
			mLastShiftTime = now;
		}
	}

	private String getWordSeparators() {
		return mWordSeparators;
	}

	public boolean isWordSeparator(int code) {
		String separators = getWordSeparators();
		return separators.contains(String.valueOf((char) code));
	}

	public void pickDefaultCandidate() {
		pickSuggestionManually(0);
	}

	public void pickSuggestionManually(int index) {
		if (mCompletionOn && mCompletions != null && index >= 0
				&& index < mCompletions.length) {
			CompletionInfo ci = mCompletions[index];
			getCurrentInputConnection().commitCompletion(ci);
//			if (mCandidateView != null) {
//				mCandidateView.clear();
//			}
			updateShiftKeyState(getCurrentInputEditorInfo());
		} else if (mComposing.length() > 0) {
			// If we were generating candidate suggestions for the current
			// text, we would commit one of them here. But for this sample,
			// we will just commit the current text.
			commitTyped(getCurrentInputConnection());
		}
	}

	public void swipeRight() {
		if (mCompletionOn) {
			pickDefaultCandidate();
		}
	}

	public void swipeLeft() {
		handleBackspace();
	}

	public void swipeDown() {
		handleClose();
	}

	public void swipeUp() {
	}

	public void onPress(int primaryCode) {
	}

	public void onRelease(int primaryCode) {
	}
}
