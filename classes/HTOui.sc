
HTOui {

    var <>host;
    var <>controller;

    var update;
    var curCtrl;

    *new{|host, controller|
        ^super.new.initHTOui(host, controller);
    }

    initHTOui{|hst, ctrl|

        host= hst;
        controller= ctrl;

        update= host.guiExists.notNil;
        host.uiExists= this; // notify host that controller exists 
        this.controllers(controller);
    }
    
    controllers{|which|

        var faderCCs, knobCCs;
        var buttonCCs;

        var faders, knobs;
        var master, mknob;

        ^switch(which)

        { 'nanoKontrol' } 
        { 
            // nanoKontroller cc nums
            faderCCs= [ 
                2, 3, 4, 5, 
                6, 8, 9, 12, 13
            ];

            // nanoKontroller cc nums
            knobCCs= [ 
                14, 15, 16, 17, 
                18, 19, 20, 21, 22
            ];

            buttonCCs= [
            ];

            // save last item for master fader
            faders = faderCCs.drop(-1);
            master = faderCCs.last;

            knobs  = knobCCs.drop(-1);
            mknob  = knobCCs.last;

            curCtrl= [

                faders.collect{|num,i|
                    CCResponder({|src,chan,num,val|
                        val= val/127;
                        host.ch[i][\fader].set(\amp, val);
                        host.faderVal[i]= val; 
                        // update gui
                        this.updateFunc(i, 'fader', val);
                    }, num: num);
                },

                knobs.collect{|num,i|
                    CCResponder({|src,chan,num,val|
                        val= val/127;
                        host.ch[i][\fader].set(\pan, val);
                        host.knobVal[i]= val; 
                        // update gui
                        this.updateFunc(i, 'knob', val);
                    }, num: num);
                },

                CCResponder({|src,chan,num,val|
                    val= val/127;
                    host.masterVal= val;
                    host.master.set(\amp, val);
                    this.updateFunc(nil, 'master', val);
                }, num: master),

                CCResponder({|src,chan,num,val|
                    val= val/127;
                    host.mknobVal= val;
                    // host.master.set(\amp, val);
                }, num: mknob)
            ];
        }
        { "controller does not exist".warn }
        ;
    }

    updateFunc{|ch, item, val|
        case
        { update==true } {
            defer{
                switch(item)
                { 'fader'  } { host.gui.faders[ch].value_(val) }
                { 'knob'   } { host.gui.knobs[ch].value_(val)  }
                { 'master' } { host.gui.master.value_(val)     }
                ;
            }
        }
        { host.guiExists.notNil } { update= true }
        ;
    }

    free{
        curCtrl ? curCtrl.flat.do(_.remove);
    }
}
