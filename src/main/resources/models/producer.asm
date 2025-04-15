asm producer

import ../libraries/StandardLibrary

signature:
    enum domain StatusDomain = {IDLE | HELLO_WORLD}
    dynamic out status: StatusDomain
    dynamic monitored trigger: Integer

definitions:

    main rule r_Main =
        if(trigger = 1) then
            status := HELLO_WORLD
        else
            status := IDLE
        endif

default init s0:
    function status = IDLE