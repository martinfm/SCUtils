/*
 A class that turns a launchpad to a sequencer

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

LaunchpadPlayer {
	var m_clock;
	var m_count;

	var m_lcp;
	var m_loopEnd;

	var m_window;
  var m_grid;
	var m_currentMode;
	var m_modes;

	var m_keyboardAction;
	var m_seqencerAction;
	var m_synths;

	var >onDispose;

	*new { arg loopEnd = 8;
		^super.new.init(loopEnd);
	}

	init {arg loopEnd;
	  var tempoBox;
		var startButton;

		m_loopEnd = loopEnd;

		m_modes = ['sequencer','keyboard'];

		m_grid = {false ! 8} ! 8; // array2D

		m_count = 0;

		m_lcp = Launchpad.new(true,true);//use osc

		m_synths = IdentityDictionary(20);

		m_lcp.addDependant({arg launchpad, what, val;
			switch(what,
				'grid_on',{
					if(m_currentMode == m_modes[0],{ // sequencer
						m_grid[val[0]][val[1]] = m_grid[val[0]][val[1]].not;
						launchpad.ledOn(val[0], val[1], m_grid[val[0]][val[1]].if('green','off'));
					},{ // keyboard
						launchpad.ledOn(val[0], val[1], 'yellow');
						if(m_keyboardAction.notNil,{
							m_keyboardAction.value(what, val[0], val[1]);
						});
					});
				},
				'grid_off',{
					if(m_currentMode == m_modes[1],{ // keyboard
						launchpad.ledOff(val[0], val[1]);
				  	if(m_keyboardAction.notNil,{
							m_keyboardAction.value(what, val[0], val[1]);
						});
					});
				},
				'control_on',{
					case
					{val[0] == 'horz' and: val[1] == 0} {
						this.mode_(m_modes.wrapAt(m_modes.indexOf(m_currentMode) +1));
						("mode switched to " ++ this.mode).postln;
					}
					{val[0] == 'vert' and: val[1] == 0}{
						{startButton.valueAction = (startButton.value == 0).if(1,0);}.defer;
					};

				},
				'control_off',{
				}
			);
		});


		m_clock = TempoClock.new(2);

   /* init GUI */

		m_window = Window.new(" - Launchpad Player -", Rect(500,500, 300, 50)).alwaysOnTop_(true);
		m_window.view.decorator = FlowLayout(m_window.view.bounds).gap_(15 @ 15).margin_(10 @ 10);

		tempoBox = EZNumber(m_window,
			120@20,
			"Tempo: ",
			ControlSpec.new(1,200,'lin',0.1,2),
			{|numBox| m_clock.tempo = numBox.value;},
			2,
			true
		);

		startButton = Button.new(m_window,70@20)
		.states_([["Start",nil,nil],["Stop",nil,nil]]);

		startButton.action = {arg button;
			if(button.value == 1,{
				/* start the player */
				m_clock.play({arg beats, time, clock;
					var previous = ((m_count - 1) + m_loopEnd) % m_loopEnd;

					if(m_currentMode == 'sequencer',{
						/* light next column on */
						for(0,7,{arg i;
							if(m_grid[i][previous],{
								m_lcp.ledOn(i,previous,'green');
							},{
								m_lcp.ledOff(i,previous);
							});
						});
						/* light next column on */
						for(0,7,{arg i;
							if(m_grid[i][m_count],{
								m_lcp.ledOn(i,m_count,'green','full');
							},{
								m_lcp.ledOn(i,m_count,'green','low');
							})
						});
					});

					if(m_seqencerAction.notNil,{
					  for(0,7,{arg i;
							if(m_grid[i][m_count],{
								m_seqencerAction.value(i,m_count);
							});
						});
					});

					m_count = (m_count + 1) % m_loopEnd;
					1;
				});
				},{
					/* stop the player */
					m_clock.clear;
					m_lcp.reset;

					if(m_currentMode == 'sequencer',{
						for(0,7,{arg i;
							for(0,7,{arg j;
								if(m_grid[i][j],{
									m_lcp.ledOn(i,j,'green');
								});
							});
						});

						m_lcp.controlLedOn('horz',0,'green');
						m_lcp.controlLedOn('vert',0,'green');
					});



					m_count = 0;
			});
		};

		m_window.onClose = {
			this.dispose();
		};

		this.mode = 'sequencer';
		m_window.front;
  }

	mode_ { arg mode;
		m_currentMode = mode;

		switch(mode,
		'sequencer',{
				for(0,7,{arg i;
					for(0,7,{arg j;
						if(m_grid[i][j],{
							m_lcp.ledOn(i,j,'green');
						});
					});
				});

				m_lcp.controlLedOn('horz',0,'green');
				m_lcp.controlLedOn('vert',0,'green');
		},
		'keyboard',{
				m_lcp.reset;
				m_lcp.controlLedOn('horz',0,'yellow');
		});
	}

	mode {
		^m_currentMode;
	}

	/* action has args what, row, col;
	   what is either 'grid_on' or 'grid_off'
	*/
	keyboardAction_ {arg action;
		 m_keyboardAction = action;
	}

	/*
	action has args row, col;
	*/
	seqencerAction_{arg action;
		m_seqencerAction = action;
	}

	synthOn { arg note, synth;
		m_synths.put(note,synth);
	}

	synthOff {arg note;
		m_synths.removeAt(note).set('gate',0);
	}


	dispose {
		"dispose".postln;
		if(onDispose.notNil){
			"dispose not nil".postln;
			onDispose.value(this);
		};
		m_clock.clear;
		m_lcp.dispose;
	}
}

