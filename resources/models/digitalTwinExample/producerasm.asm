asm producerasm


import ./StandardLibrary

signature:
    enum domain ProducerState = {RUN}
    dynamic controlled state: ProducerState

    // input: due valori da console
    dynamic monitored consoleInputTemp: Integer
    dynamic monitored consoleInputHR: Integer

    // output: li inoltra al consumer
    dynamic out outTemp: Integer
    dynamic out outHR: Integer

definitions:

    main rule r_Main =
        par
            // instrada la temperatura
            if (isUndef(consoleInputTemp)) then
                outTemp := 0
            else
                outTemp := consoleInputTemp
            endif

            // instrada il battito cardiaco
            if (isUndef(consoleInputHR)) then
                outHR := 0
            else
                outHR := consoleInputHR
            endif
        endpar

default init s0:
    function state = RUN