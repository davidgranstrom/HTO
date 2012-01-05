
HTOgui {

    var <>host;
    var <faders, <knobs, <master; 

    var width= 800, height= 400;
    var wh, hh;

    *new{|host|
        ^super.new.initGUI(host);
    }

    initGUI{|hst|

        host= hst;

        width=  75*host.mixerChannels; 
        height= width*0.5;

        wh= width*0.5;
        hh= height*0.5;

        host.guiExists= this; // notify host that gui exists 
        this.draw;
    }

    draw{

        var win, decor;

        var num= host.mixerChannels;
        var spec= [ 0, 1, \lin ].asSpec;

        win= Window("HTO", Rect(128, 64, width, height));

        win.view.decorator = decor = FlowLayout(win.view.bounds);
        decor.gap= 2@2;

        knobs= num.collect{|i|
            EZKnob(win, 50@50, nil, spec, initVal: 0.5, initAction: true)
                .action_({|knob| 
                    host.ch[i][\fader].set(\pan, knob.value); 
                });
        };

        decor.nextLine;

        faders= num.collect{|i|
            EZSlider(win, 50@200, "fader"+i, spec, layout: \vert)
                .action_({|slider| 
                    host.ch[i][\fader].set(\amp, slider.value); 
                });
        };

        // master fader
        master= EZSlider(win, 50@200, "master", spec, layout: \vert)
            .action_({|slider| 
                host.master.set(\amp, slider.value); 
            });

        win.front;             
    }

    makeWin{ this.draw } // temp: re-open window 
}
