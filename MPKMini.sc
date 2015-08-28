
/*
Graphical panel for midi controller.

init : inits the gui and the basic midi
dipose : disposes the gui and the midi


*/
MPKMini {
  /* the number of channels, it cannot exceed 16 */
  var m_numChannels;
  var m_numKnobs;
  /* the current channel */
  var m_channel;
  var m_noteArray;
  var m_gateArray;
  var m_noteListeners;
  var m_knobListeners;
  var m_defaultNoteListener;
  var m_defaultKnobListener;
  var m_sourceSelector;
  var m_sourceSelectorBtn;
  var m_selectedSource;
  var m_connectedSource;
  var m_knobMatrix;
  var m_knobsPerLine;
  var m_midiNotes;
  var m_window;
  var m_dumpMidi;
  var m_midi;
  var m_synths;

  /* setter for callback when this panel is disposed */
  var >onDispose;

  *new { arg numChannels = 16, numKnobs = 8, knobsPerLine = 4;
    if(numChannels > 16,{
      Error("Max num channels allowed is 16. numChannels = "++numChannels).throw;
    });
    ^super.new.init(numChannels, numKnobs, knobsPerLine);
  }

  init { arg numChannels, numKnobs, knobsPerLine;
    var midiSources;

    m_numChannels = numChannels;
    m_numKnobs = numKnobs;

    m_channel = 1;
    m_noteArray = 0 ! numChannels;
    m_gateArray = 0 ! numChannels;

    /* the default note lister, just prints out all the note infos */
    m_defaultNoteListener = {arg note, gate, veloc, chan;
      ("Default note listener. channel:"++chan++
        " note:"++note++" gate:"++gate++
        " veloc:"++veloc
      ).postln;
    };

    m_defaultKnobListener = {arg val, ccnum, chan;
      ("Default knob listener. channel:"++chan++
        " control num:"++ccnum++" value:"++val).postln;
    };

    m_noteListeners = Array.fill(numChannels,{m_defaultNoteListener});
    m_knobListeners = Array.fill(numChannels,{
      Array.fill(numKnobs,{m_defaultKnobListener});
    });

    /* numKnobs knobs for each channel */
    m_knobMatrix = (0 ! numKnobs) ! numChannels;
    m_knobsPerLine = knobsPerLine;

    /* trigger listenerers for this channel */
    super.addDependant({arg model, what, val;
      switch(what,
        'note',{
          m_noteListeners[m_channel].value(
            val[0], // note
            val[1], // gate
            val[2],  // velocity
            m_channel   //channel
          );
        },
        'knob',{
          /* val[0] is the cc number*/
          m_knobListeners[m_channel][val[0]].value(
            val[1], // cc value
            val[0], // cc num
            m_channel // channel
          );
        }
      );
    });


    m_synths = IdentityDictionary(20);
    m_midiNotes = ["C","C#","D","D#","E","F","F#","G","G#","A","A#","B"];
    m_dumpMidi = false;

    midiSources = this.initMIDI;
    this.initGUI(midiSources);
  }

  *usage {
    ("MidiPanel: noteListener_( channel, {|note, gate, veloc, chan|}) knobListener_(channel, ccnum, {|val, ccnum, chan|})").postln;
  }
  /* --- GETTERS AND SETTERS --- */
  channel {
    ^m_channel;
  }

  channel_{arg channel;
    m_channel = channel.clip(1,m_numChannels);
    super.changed('channel',m_channel);
    ^this;
  }

  /* returns the knob bus at knobIndex of the current channel */
  knob {arg knobIndex;
    ^m_knobMatrix[m_channel][knobIndex];
  }

  knob_ {arg knobIndex,val;
    m_knobMatrix[m_channel][knobIndex] = val;
    super.changed('knob',[knobIndex,val]);
    ^this;
  }

  note {
    ^m_noteArray[m_channel];
  }

  note_ {arg note, gate, velocity;
    m_gateArray[m_channel] = gate;
    if(gate == 1) {
      m_noteArray[m_channel] = note;
    };
    super.changed('note',[note,gate,velocity]);
    ^this;
  }

  noteListener_ {arg channel, function;
    if(function.isNil){
       m_noteListeners.wrapPut(channel,m_defaultNoteListener);
     }{
       m_noteListeners.wrapPut(channel,function);
    };
    ^this;
  }

  knobListener_ {arg channel, ccnum, function;
    if(function.isNil){
      m_knobListeners[channel][ccnum] = m_defaultNoteListener;
     }{
      m_knobListeners[channel][ccnum] = function;
    };
    ^this;
  }

  connectedSource {
    ^m_connectedSource;
  }

  synthOn { arg note, synth;
    m_synths.put(note,synth);
  }

  synthOff {arg note;
    m_synths.removeAt(note).set('gate',0);
  }

  knobsPerLine_ {arg num;
    m_knobsPerLine = num;
    m_window.refresh;
  }

  enableDumpMIDI { arg dump;
    m_dumpMidi = dump;
  }

  /* ---- GUI  ---- */
  initGUI { arg midiSources;
    var knobs, guiUpdater, model, channel, slider, noteText, popUpItems;
    model = this;

    m_window = Window("MPK Mini",Rect(100,100,490,330), resizable: false).alwaysOnTop_(true);
    m_window.view.decorator = FlowLayout(m_window.view.bounds).gap_(10 @ 11).margin_(10 @ 10);

    /* slider to select the channel */
    slider = EZSlider.new(
      parent:m_window,
      bounds:(320@20),
      label:"Channel:",
      labelWidth:45,
      controlSpec:ControlSpec.new(1,16,'lin',1,1),
      action:{ arg slider;
        model.channel_(slider.value.asInteger);
      }
    );

    /* text box for the note */
    noteText = EZText (
      parent:m_window,
      bounds:(100@20),
      label:"Note:"
    );
    /* init to empty text */
    noteText.textField.value = "";
    // m_window.view.decorator.nextLine;
    m_window.view.decorator.nextLine;

    knobs = Array.fill(8,{arg i;
      var gap = 10;
      var len = 110;
      var k = EZKnob.new(
        parent:m_window,
        bounds:(len @ len),
        initVal: 0,
        controlSpec: 'midi',
        action: { arg knob;
          model.knob_(i,knob.value);
        }
      );
      /* new line every m_knobsPerLine knobs */
      if(i==(m_knobsPerLine-1),{
        m_window.view.decorator.nextLine;
      });
      k;
    });

    2.do{m_window.view.decorator.nextLine};


    /* add source selection*/
    popUpItems = midiSources.collect{arg item;
      /* upon changing the popupitems the currentSource is set to the selected */
      (item.asSymbol -> {m_selectedSource = item; m_selectedSource.postln});
    };

    m_sourceSelector = EZPopUpMenu.new(m_window,200@22,"Midi Source:",popUpItems,initAction:true,labelWidth:60);
    m_sourceSelectorBtn = Button.new(m_window,100@22).states_([["Connect",nil,nil],["Disconnect",nil,nil]]);
    m_sourceSelectorBtn.action = {arg button;
    if(m_selectedSource.notNil){
        if(button.value == 1){ // connect
          var src = MIDIClient.sources.detect{|a| a.name == m_selectedSource};
          MIDIIn.connect(0, src.uid);
          m_connectedSource = src;
          ("Connected to "++src).postln;
        }{ // disconnect
          var src = MIDIClient.sources.detect{|a| a.name == m_selectedSource};
          ConfirmDialog.new("Disconnect MIDI ?", {
            MIDIIn.disconnect(0, src.uid);
            m_connectedSource = nil;
            ("Disconnected from "++src).postln;
          });

        };
      }
    };

    /* a view of the midi model that updates the GUI */
    guiUpdater = {arg model,what,val;
      /* switch returns a function to defer */
      switch (what,
        'channel',   {
          {
            (0..m_numKnobs-1).do{|i| knobs[i].value = m_knobMatrix[val][i]};
            slider.value_(val);
          }
        },
        /* write the note on the text field */
        'note', {
          {
            if(val[1] == 1, // gate
              { noteText.textField.value = m_midiNotes.wrapAt(val[0]);},
              { if(val[0] == model.note, {noteText.textField.value = "";}) }
            )
          }
        },
        /* knobs */
        'knob', {
          { knobs.at(val[0]).value_(val[1]); }
        }
      ).defer; // executes in GUI thread
    };
    model.addDependant(guiUpdater);

    m_window.onClose = {
      this.removeDependant(guiUpdater);
      this.dispose;
    };
    m_window.front;
  }

  /**
   diposes the window if the MIDIpanel has been closed
   programmatically rather than closeing the window itself
  */
  disposeGui {
    if(m_window.notNil){
      if(m_window.isClosed.not){
        m_window.close;
      };
    };
  }

  /* ----  MIDI ---- */

  initMIDI {
    var on,off,cc;

    MIDIClient.init;
    // MIDIIn.connectAll;

    on = MIDIFunc.noteOn({ arg veloc, note, chan, src;
      if(m_dumpMidi){
        "midi on: ".post; [veloc, note, chan, src].postln;
      };
      this.note_(note,1,veloc);
    });

    off = MIDIFunc.noteOff({ arg veloc, note, chan, src;
      if(m_dumpMidi){
        "midi off: ".post; [veloc, note, chan, src].postln;
      };
      this.note_(note,0,veloc);
    });

    cc = MIDIFunc.cc({arg val, num, chan, src;
      if(m_dumpMidi){
        "cc: ".post; [val, num, chan, src].postln;
      };
      this.knob_(num-1,val);
    });

    /* for freeing up later */
    m_midi = [on,off,cc];

    /* returns a collection of available sources*/
    ^MIDIClient.sources.collect({arg item; item.name});
  }
  //
  // connectMIDI {arg source;
  //   MIDIClient.sources.detect{|a|a.name.contains(source)};
  // }

  disposeMidi {
    if(m_midi.notNil,{
      m_midi.do({arg item,index; item.free;});
    });

    MIDIClient.disposeClient;
    "MPK Mini freed".postln;
  }

  dispose {
    this.disposeGui;
    this.disposeMidi;

    if(onDispose.notNil){
      onDispose.value(this);
      onDispose = nil;
    };
  }
}
