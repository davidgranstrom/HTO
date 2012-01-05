// HTO(speakers, mixerChannels, numAudioChannels)
(
s.waitForBoot{
    // create a 2 speaker instance of HTO with 8 mixer channels 
    // last fader is reserved for the master fader
    h= HTO(2, 9); 
    s.sync;
    h.loadAudioFile("sounds/a11wlk01.wav"); 
}
)

h.play; 
s.queryAllNodes;

// HTO mixer is zeroed by default
h.ch[0].fader.set(\amp, 0.5, \pan, 0); // channel 1 -> L
h.ch[1].fader.set(\amp, 0.5, \pan, 1); // channel 2 -> R

h.master.set(\amp, 1);

h.stop; 

h.loadAudioFile; // load another file (opens dialog)
h.play;          // the most recently loaded file is cued for playback
h.stop; 

h.library; // show existing audio files 

// if 'filename' is an absolute path, it will be added to the library 
h.play('FILENAME'); 

// access the library 
h.play(h.library[0]);

/*=================================================================
 PATCHING
=================================================================*/

h.ch[0].input.set(\in, h.audiobus[1]); // reverse the source inputs (won't work w/ example file, which is mono..)
h.ch[1].input.set(\in, h.audiobus[0]); 

// a HTO fader receives a mono signal and outputs stereo by default
h.ch[1].fader.set(\mute, 1);
h.ch[0].fader.set(\pan, 0.5); // center pan 

s.meter;

h.ch[0].output.set(\routing, [ 0, 0 ]); // no hardware output
h.ch[0].output.set(\routing, [ 1, 0 ]); // left channel only
h.ch[0].output.set(\routing, [ 1, 1 ]); // both

// the size of the routing matrix depends 
// on the 'speakers' arg of the HTO instance 
h.ch[0].output.set(\routing, [ 1, 1, 0, 0, 0, 0, 0, 0 ]); // 8 channel instance 

// the whole array doesn't need to be written out if not necessary
h.ch[0].output.set(\routing, [ 1, 1, 0, 1, 1 ]); // hardware outputs: [ 0, 1, 3, 4 ] 

//-----------------------------------------------------------------

h.ch.do{|c| c.fader.set(\mute, 1) };"" // incremental assignments
h.ch.do{|c| c.fader.set(\mute, 0) };""

h.master.set(\mute, 1) // easier :)

h.ch[0..3].do{|c| c.insertFX.(\LPF) };"" // add LPF's to the 4 first channels 

s.queryAllNodes;

h.ch[0..3].do{|c| c.lpf.set(\cfreq, 0.05) };"" // center freq is set 0-1 and mapped to default: 20, 20000
h.ch[0..3].do{|c| c.lpf.set(\loVal, 20, \hiVal, 5000) };"" // center freq is set 0-1 and mapped to default: 20, 20000

h.ch[0..3].do{|c| c.insertFX.(\LPF) };"" // call again to release
s.queryAllNodes;

h.gui; // gui test

HTOui(h, 'nanoKontrol'); // use a controller

h.free; 

/*=================================================================
 METHOD OVERVIEW
=================================================================*/

// MIXERCHANNEL METHODS
ch[num].fader.set(\amp, \pan, \mute); 
ch[num].input.set(\in, audiobus[ch]); 
ch[num].output.set(\routing, [speakerArray]);
ch[num].insertFX.(\FX); // \HPF or \LPF 
ch[num].hpf.set(\cfreq, \loVal, \hiVal);
ch[num].lpf.set(\cfreq, \loVal, \hiVal);
master.set(\amp, \mute);

// INSTANCE METHODS
loadAudioFile; // "/path/to/file" or nil. default: nil
library        // show added audio files
play;          // 'library Name' or "/path/to/file". default: nil
stop;
free;

/*=================================================================

 NOTES
 
 All input args on the mixer channels are 
 standardized to accept values from 0.0 to 1.0
 The thought is that mapping LFO's internally in HTO or using 
 other external devices with other protcols e.g OSC, will be easy 
 to switch between and exchange on the fly w/o worrying about ranges.

=================================================================*/
// Example:

h.free;
(
s.waitForBoot{
    h= HTO(2, 9); 
    s.sync;
    h.loadAudioFile("sounds/a11wlk01.wav"); 
}
)

h.play;
h.master.set(\amp, 1);

c= Bus.control(s, 1);
x= play{|freq=7| Out.kr(c, FSinOsc.kr(1/freq).range(0, 1)) };

h.ch[0].fader.set(\amp, 0.5, \pan, c.asMap);
x.set(\freq, 4);

// map the same bus to control amplitude of input synth 
h.ch[0].input.set(\amp, c.asMap); // NOTE: "pre-fader"

h.free; x.free; c.free; 

//=================================================================
