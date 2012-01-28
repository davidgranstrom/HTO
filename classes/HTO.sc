
HTO {

    var <>speakers;
    var <>mixerChannels;
    var <>numAudioChannels;

    var <audiobus, <mixerbus, <masterbus;

    var server;
    var src, xfer, fx, channels, master;
    var fxchain; 

    var srcsynth, masterFader;
    var allXfer, allFaders, channelStrip;
    var curBuf;

    var lib;

    var <presets;
    var <curFile;

    var <>faderVal, <>knobVal, <>hi_btnVal, <>lo_btnVal;
    var <>masterVal,<>mknobVal;

    var <isPlaying, <srcReady; 
    var <>cursorPos, cursorMove;
    var <>uiExists, <>guiExists;

    *new{|speakers=2, mixerChannels=9, numAudioChannels=8|
        ^super.new.initHTO(speakers, mixerChannels, numAudioChannels);
    }

    initHTO{|spkrs, mxrChns, numChs|

        speakers         = spkrs;
        mixerChannels    = mxrChns - 1;
        numAudioChannels = numChs;

        Server.default   = server = Server.local;
                         
        src              = Group.head(server);
        xfer             = Group.after(src);
        fx               = Group.after(xfer);
        channels         = Group.after(fx);
        master           = Group.after(channels);
                         
        audiobus         = { Bus.audio(server, 1) } ! numAudioChannels; 
        mixerbus         = { Bus.audio(server, 1) } ! mixerChannels;
        masterbus        = { Bus.audio(server, 1) } ! speakers;
                         
        // store values for faders, buttons and knobs
        faderVal         = Array.newClear(mixerChannels);
        knobVal          = Array.newClear(mixerChannels);
        hi_btnVal        = Array.newClear(mixerChannels);
        lo_btnVal        = Array.newClear(mixerChannels);

        // store all channels in the mixer 
        allXfer          = Array.newClear(mixerChannels);
        allFaders        = Array.newClear(mixerChannels); 

        isPlaying        = false;
        srcReady         = Condition.new;

        // add slots to store effects synths in
        // one array -> FX
        fxchain= [ 

             Array.newClear(mixerChannels), 
             Array.newClear(mixerChannels)
        ];

        // global storage for audiofiles
        lib= (); 

        // create a global dictionary to store presets in.
        // add a default preset
        presets= (

            default: 
            (
                username: \default,
                mixer: () // store faderVal, knobVal etc. in here..
            )
        );

        ^if(server.serverRunning, {
            CmdPeriod.doOnce{ this.free };
            this.loadDefs(speakers);
        }, {
            "please boot the server first".throw;
            this.halt;
        });
    }

    loadDefs{|speakers|

        fork{
            var lag= 0.075;

            SynthDef(\HTO_xfer, {|in, out, amp=1|
                var src= In.ar(in);
                amp= amp.clip(0, 1);
                Out.ar(out, src*amp);
            }, [ 0, 0, lag ]).add;

            SynthDef(\HTO_fader, {|in, pan=0.5, amp=0, mute=0|
                var src= In.ar(in)*abs(mute-1);
                var matrix= \routing.kr(0!speakers);
                var output; 
                pan=  pan.linlin(0, 1, -1, 1);
                amp=  amp.clip(0, 1);
                mute= mute.clip(0, 1);
                output= Pan2.ar(src, pan)*matrix;
                Out.ar(masterbus[0], output*amp);
            }, [ 0, lag, lag, lag ]).add;

            SynthDef(\HTO_surroundFader, {|out, in, pan, firstSpeaker, amp=0, mute=0|
                var src= In.ar(in)*abs(mute-1);
                pan=  pan.linlin(0, 1, -1, 1);
                amp=  amp.clip(0, 1);
                mute= mute.clip(0, 1);
                Out.ar(masterbus[0], PanAz.ar(speakers, src, pan, orientation: firstSpeaker)*amp);
            }).add;

            SynthDef(\HTO_HPF, {|in, cfreq=0, loVal=20, hiVal=20000, gate|
                var src= In.ar(in);
                var env= EnvGen.ar(Env([0,1,0], [0.01,0.01], \sine, 1), gate, doneAction:2);
                cfreq= cfreq.linlin(0, 1, loVal, hiVal).clip(20, 20000);
                ReplaceOut.ar(in, HPF.ar(src, cfreq)*env);
            }, [ 0, lag, lag, lag ]).add;

            SynthDef(\HTO_LPF, {|in, cfreq=1, loVal=20, hiVal=20000, gate|
                var src= In.ar(in);
                var env= EnvGen.ar(Env([0,1,0], [0.01,0.01], \sine, 1), gate, doneAction:2);
                cfreq= cfreq.linlin(0, 1, loVal, hiVal).clip(20, 20000);
                ReplaceOut.ar(in, LPF.ar(src, cfreq)*env);
            }, [ 0, lag, lag, lag ]).add;

            SynthDef(\HTO_masterFader, {|amp=0, mute=0|
                var src= In.ar(masterbus);
                amp= amp.clip(0, 1);
                mute= mute.clip(0, 1);
                src= src * mixerChannels.reciprocal.sqrt;
                Out.ar(0, src*amp*abs(mute-1)); // start output at first speaker
            }, [ lag, lag ]).add;

        server.sync;
        this.initMixer(speakers);
        }
    }

    initMixer{|speakers|

        var matrix= 0 ! speakers;
        var num= (mixerChannels/2).round.asInt;
        var bus= num.collect{|i| var c; i= i*2; c= matrix.copy; c.wrapPut([i,(i+1)], 1) }.stutter;

        fork{
            mixerChannels.do{|i|

                var in=  i % 2; // assume stereo 
                
                allXfer[i]=   Synth.head(xfer, \HTO_xfer, [\in, audiobus[in], \out, mixerbus[i]]);
                allFaders[i]= Synth.tail(channels, \HTO_fader, [\in, mixerbus[i], \routing, bus[i]]); 
            };
            masterFader= Synth.tail(master, \HTO_masterFader); 

            server.sync;

            channelStrip= {|i|

                (
                    fader:    allFaders[i],
                    input:    allXfer[i],
                    output:   allFaders[i],
                    insertFX: {{|x| this.insertFX(x, i) }},
                    hpf:      { fxchain[0][i] },
                    lpf:      { fxchain[1][i] }
                )
            };
            channelStrip= channelStrip ! mixerChannels;

            "Output routing matrix:".postln;
            bus.do{|b,i| ("ch" ++ i ++ ": ").post; b.postln; };
        }
    }

    ch{
        ^channelStrip;
    }

    master{
        ^masterFader;
    }

    insertFX{|which, ch|

        var hpf, lpf, eq;
        var in= mixerbus[ch];

        ^switch(which)

        { 'HPF' } {
            
            hpf= if(fxchain[0][ch].isNil, { 
                fxchain[0][ch]= Synth.tail(fx, \HTO_HPF, [\in, in, \gate, 1]);
            }, {
                fxchain[0][ch].set(\gate, 0); 
                fxchain[0][ch]= nil;
            });
            hpf[ch]; // return the synth, not the whole array 
        }
        { 'LPF' } {    
        
            lpf= if(fxchain[1][ch].isNil, { 
                fxchain[1][ch]= Synth.tail(fx, \HTO_LPF, [\in, in, \gate, 1]);
            }, {
                fxchain[1][ch].set(\gate, 0); 
                fxchain[1][ch]= nil;
            });
            lpf[ch]; 
        }
        { "fx does not exist".warn }
        ;
    }

    loadAudioFile{|filepath|

        var name, sf;
        var numChannels, numFrames;  
        var sampleRate, duration;

        if(filepath.notNil, { filepath= PathName(filepath) });

        case 

        { filepath.isNil } {
            
            Dialog.getPaths({|path|  

                path.do{|pth|
                    name= PathName(pth).fileNameWithoutExtension.asSymbol;
                    sf= SoundFile.openRead(pth);

                    numChannels = sf.numChannels;
                    numFrames   = sf.numFrames;
                    sampleRate  = sf.sampleRate;
                    duration    = numFrames/sampleRate;
                    sf.close;

                    if(lib[name].isNil, {
                        lib.put(name, [ pth, numChannels, numFrames, sampleRate, nil, duration ]);
                        curFile= name;
                        this.prepareForPlay(name, numChannels);
                    }, {
                        inform("File" + name.asCompileString + "already exists in library!");
                    });
                };
            });
        } 
        { filepath.isFile } {

            filepath= [ filepath.fullPath ];
            filepath.do{|pth|
                name= PathName(pth).fileNameWithoutExtension.asSymbol;
                sf= SoundFile.openRead(pth);

                numChannels = sf.numChannels;
                numFrames   = sf.numFrames;
                sampleRate  = sf.sampleRate;
                duration    = numFrames/sampleRate;
                sf.close;

                if(lib[name].isNil, {
                    lib.put(name, [ pth, numChannels, numFrames, sampleRate, nil, duration ]);
                    curFile= name;
                    this.prepareForPlay(name, numChannels);
                }, {
                    inform("File" + name.asCompileString + "already exists in library!");
                });
            }
        }
        { filepath=='internal' } { // FIX ME

            SoundFile.collect("/path/to/HTO/Audio/*").do{|f| 

                name= PathName(f.path).fileNameWithoutExtension.asSymbol;
                sf= SoundFile.openRead(f.path);

                numChannels = sf.numChannels;
                numFrames   = sf.numFrames;
                sampleRate  = sf.sampleRate;
                duration    = numFrames/sampleRate;
                sf.close;

                if(lib[name].isNil, {
                    lib.put(name, [ f.path, numChannels, numFrames, sampleRate, nil, duration ]);
                    curFile= name;
                    fork{
                        this.prepareForPlay(name, numChannels);
                        srcReady.hang; // wait here for srcdef to build
                    }
                }, {
                    inform("File" + name.asCompileString + "already exists in library!");
                });
            };
        }
        ;
    }

    play{|file, loop=1|
        if(isPlaying.not, {
            file ?? { file= curFile ?? { "No audiofile(s) found!".throw } };
            fork{

                var buf, startPos;    
                var path        = lib[file][0];
                var numChannels = lib[file][1];
                var numFrames   = lib[file][2];
                var sampleRate  = lib[file][3];
                var name        = lib[file][4];
                var update      = 0.03;

                startPos  = cursorPos ? 0;
                numFrames = numFrames + 1;

                allXfer.do{|syn, i| 
                    var in= i % numChannels; 
                    syn.set(\in, audiobus[in]);
                };
                buf= Buffer.cueSoundFile(server, path, startPos, numChannels); // bufferSize: 262144
                server.sync;
                srcsynth= Synth.head(src, name, [\buf, buf, \loop, loop]);
                // advance the play head
                cursorMove= 
                fork{
                    inf.do{|i| 
                        defer{ 
                            cursorPos= (startPos+((i*update)*sampleRate).asInteger)%numFrames 
                        };  
                    update.wait; 
                    }
                };
                curBuf= buf;
                curFile= file;
                isPlaying= true;
            }
        });
    }

    prepareForPlay{|name, numChannels|
        fork{
            var defname= 'HTO_src_' ++ name;
            defname= defname.asSymbol;
            SynthDef(defname, {|buf, gate=1, loop| 
                    var env= EnvGen.ar(Env([0,1,0], [0.02,0.02], \sine, 1), gate, doneAction:2);
                    var src= VDiskIn.ar(numChannels, buf, BufRateScale.kr(buf), loop: loop ); 
                    Out.ar(audiobus[0], src*env);
            }).add;
            server.sync;
            lib[name][4]= defname;
            srcReady.unhang; 
            inform("File" + name.asCompileString + "was successfully loaded."); 
        }
    }

    // in absence of gui.. 
    playFrom{|pos, file, loop|
        if(isPlaying.not, {
            cursorPos= (pos*60)*lib[file][3];
            this.play(file, loop);
        });
    }

    stop{
        if(isPlaying, { 
            srcsynth.set(\gate, 0); srcsynth= nil; 
            curBuf.close; curBuf.free; curBuf= nil;
            cursorMove.stop; cursorMove= nil;
            isPlaying= false;
        });
    }

    reset{
        this.stop; 
        cursorPos= nil;
    }

    library{
        ^lib.collect(_.last)
            .getPairs.collect{|x,i|
                if(i.odd, { x.asTimeString }, { x })
            }
    }

    gui{
        if(guiExists.isNil, {
            guiExists= HTOgui(this);
        });
        ^guiExists;
    }

    controller{|which|
        if(uiExists.isNil, {
            uiExists= HTOui(this, which);
        });
        ^uiExists;
    }

    savePreset_ {|user|
        ^if(presets[user].isNil, {
            presets.put(user, (user: user));
        });
    }

    loadPreset{|user|
        ^if(presets[user].notNil, {
            presets[user]
        }, {
            "please save a preset first!".throw;
        });
    }

    free{
        [ src, xfer, fx, channels, master ].do(_.free);
        curBuf   !? { curBuf.close; curBuf.free };
        uiExists !? { uiExists.free };
        isPlaying= false;
    }   

    debug{
        ^lib;
    }

    doesNotUnderstand{|selector ... args|  
        var channels = channelStrip[selector];
        ^channels ?? { super.doesNotUnderstand(selector, args) };
    }
}

/*
=====
TODO: 
=====

    * presets
    * add LFO's similar to insertFX from channelStrip?
        e.g. {{|x| this.insertLFO(x, param, i) }}
*/

