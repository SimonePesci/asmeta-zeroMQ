asm producer

import ./StandardLibrary

signature:
    enum domain StatusDomain = {IDLE | HELLO_WORLD}
    dynamic monitored trigger: Integer
    dynamic monitored sharedValue: Integer

    dynamic out incomingStatus: StatusDomain
    out resultShared: Integer

definitions:

    main rule r_Main =
            if(trigger = 1) then
                par
                    incomingStatus := HELLO_WORLD
                    if (isUndef(sharedValue)) then
                        resultShared := 0
                    else
                        resultShared := 1
                    endif
                endpar
            else
                incomingStatus := IDLE
            endif

default init s0:
    function incomingStatus = IDLE