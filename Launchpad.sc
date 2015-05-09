/*
 Class to handle Launchpad in SuperCollider

 Copyright (C) 2015  Fiore Martin

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

Launchpad {
  classvar bits;

  var m_leds;
  var m_vertControls;
  var m_horzControls;
  var m_out;
  var m_listeners;

  *initClass {
    bits = (
      double_buffer: 0,
      nobuffer: 0xC,
      clear: 1 << 3,
      copy: 1 << 2,

      off: (
        full:0
      ),

      red: (
        low:1,
        medium:2,
        full: 3
      ),
      green: (
        low: 0x10,
        medium: 0x20,
        full: 0x30
      ),

      amber: (
        low: 0x10 | 1,
        medium: 0x20 | 2,
        full: 0x30 | 3
      ),

      yellow: (
        full:0x30 | 2
      ),

      orange: (
        full:0x20 | 3
      )
    );
  }

  *new { arg useOSCsrc = false, useOSCdst = false;
		^super.new.init(useOSCsrc, useOSCdst);
  }

  init {arg useOSCsrc, useOSCdst;
    var listenerFuncs;
		/* array2D of arrays with [message for launchpad, color, brightness ] */
    m_leds = {[0,'off','full'] ! 8} ! 8; // array2D
    m_vertControls = 'off' ! 8;
    m_horzControls = 'off' ! 8;

    listenerFuncs = IdentityDictionary[
      'noteon' -> { arg veloc, note,chan, src;
        if((note - 8) % 16 == 0,{
          /* right column of control buttons */
          this.changed('control_on', [ 'vert', (note-8)/16 ]);
        },{
          /* grid */
          /* calculate row and clumn from the note number */
          var round = note.div(16) * 16;
          this.changed('grid_on', [note.div(16),note-round]);
        });
      },

      'noteoff' -> { arg veloc, note, chan, src;
        if((note - 8) % 16 == 0,{
          /* right column of control buttons */
          this.changed('control_off', [ 'vert', (note-8)/16 ]);
        },{
          /* calculate row and clumn from the note number */
          var round = note.div(16) * 16;
          this.changed('grid_off', [note.div(16),note-round]);
        });
      },

      'control' -> {arg val, num, chan, src;
        var onoff = (val == 0).if({'control_off'},{'control_on'});

        this.changed(onoff,['horz',num-104]);
      }
    ];

    /* init the communication */
		this.initOSC(listenerFuncs, useOSCsrc, useOSCdst);
		this.initMIDI(listenerFuncs, useOSCsrc.not, useOSCdst.not);

		this.controlLedOn('horz',7,'green');
  }

  initOSC { arg listenerFuncs, initSrc, initDst;

		if(initDst,{
			"Launchapad init destination using OSC".postln;
			m_out = OSCout.new;
		});

		if(initSrc,{
      ("Launchpad init source using OSC on port "++NetAddr.langPort).postln;
			m_listeners = [
				OSCFunc({arg msg, time, addr, recvPort;
					listenerFuncs['noteon'].value(msg[3],msg[2],msg[1]);
				},'/midi/noteon'),

				OSCFunc({arg msg, time, addr, recvPort;
					listenerFuncs['noteoff'].value(msg[3],msg[2],msg[1]);
				},'/midi/noteoff'),

				OSCFunc({arg msg, time, addr, recvPort;
					listenerFuncs['control'].value(msg[3],msg[2],msg[1]);
				},'/midi/cc')
			];
		});

  }

  initMIDI { arg listenerFuncs, initSrc, initDst;
    var midiSource;
    var midiDest;
    var noteOn;
    var noteOff;
    var cc;


		if(initSrc,{
		  "Launchpad init source using MIDI".postln;
			/* init source and destination */
			MIDIClient.init;
			midiSource = MIDIClient.sources.detect{|a|a.name.contains("Launchpad")};
			if(midiSource.isNil, {
				"Launchpad not connected".throw;
			});
			midiDest = MIDIClient.destinations.detect{|a|a.name.contains("Launchpad")};

			/* connect in and out */
			MIDIIn.connect(0, midiSource.uid);

			m_listeners = [
				MIDIFunc.noteOn(listenerFuncs['noteon']),
				MIDIFunc.noteOff(listenerFuncs['noteoff']),
				MIDIFunc.cc(listenerFuncs['control'])
			];
		});

		if(initDst,{
			"Launchpad init destination using MIDI".postln;
			m_out = MIDIOut.new(0,midiDest.uid);
		});
  }

  reset {
		/* turns all leds off */
    m_out.control(0,0,0);
    this.controlLedOn('horz',7,'green');
  }

  ledOn {arg row, col, color, brightness = 'full', buffer = 'nobuffer' ;
    var msg = bits['nobuffer'] | bits[color][brightness];

    m_leds[row][col] = [msg,color,brightness];
    m_out.noteOn(0,(16*row)+col, msg);
  }

  ledOff { arg row, col;
    m_leds[row][col] = [0,'off','full'];
    m_out.noteOff(0,(16*row)+col, 0);
  }

  getLedColor{ arg row, col;
    ^(m_leds[row][col]).at(1);
  }

  getLedBrightness{ arg row, col;
    ^(m_leds[row][col]).at(2);
  }

  setColumn{ arg col, color, brightness = 'full',buffer = 'nobuffer';
    for(0,7,{arg i; this.ledOn(i,col,color,brightness,buffer)});
  }

  setRow{ arg row, color, brightness = 'full', buffer = 'nobuffer';
    for(0,7,{arg i; this.ledOn(row,i,color,brightness,buffer)});
  }

  /* orientation can be either 'vert' or 'horz' */
  controlLedOn{ arg orientation, position, color;
    var msg = bits['nobuffer'] | bits[color]['full'];

    if(orientation == 'horz',{
      m_horzControls.put(position,color);
      m_out.control(0,104+position,msg);
      ^this;
    });

    if(orientation == 'vert',{
      m_vertControls.put(position,color);
      m_out.noteOn(0,8+(position*16),msg);
      ^this;
    });

    ("wrong orientation:"++orientation).throw;
  }

  controlLedOff{arg orientation, position;

    if(orientation == 'horz',{
      m_horzControls.put(position,'off');
      m_out.control(0,104+position,bits.nobuffer);
      ^this;
    });

    if(orientation == 'vert',{
      m_vertControls.put(position,'off');
      m_out.noteOff(0,8+(position*16),0);
      ^this;
    });

    ("wrong orientation:"++orientation).throw;
  }

  getControlLedColor{ arg orientation, position;
    if(orientation == 'horz',{
      ^m_horzControls.at(position);
    });

    if(orientation == 'vert',{
      ^m_vertControls.at(position);
    });

    ("wrong orientation:"++orientation).throw;
  }

  setUpdatingBuffer {arg index;
    if(index == 0,{
      m_out.control(0,0,49);
      },{
      m_out.control(0,0,52);
    });
  }

  dispose {
    this.reset;
    if(m_listeners.notNil,{
      m_listeners.do(_.free);
    });
  }

}

OSCout {
  var dest;

  *new {
    ^super.new.init();
  }

  init {
    dest = NetAddr.new("127.0.0.1", 5001);
  }

  noteOn { arg channel, note = 60, velocity = 64;
    dest.sendMsg("/midi/noteon",channel,note,velocity);
  }

  noteOff { arg channel, note = 60, velocity = 64;
    dest.sendMsg("/midi/noteoff",channel,note,velocity);
  }

  control { arg channel, ctlNum = 7, val = 64;
    dest.sendMsg("/midi/cc",channel,ctlNum,val);
  }

}
