package vproxy.fstack;

import vproxy.util.LogType;
import vproxy.util.Logger;

import java.util.List;

public class FStack implements IFStack {
    @Override
    public void ff_init(List<String> args) {
        String[] arr = new String[args.size()];
        args.toArray(arr);
        ff_init0(arr);
    }

    native private void ff_init0(String[] args);

    @Override
    public void ff_run(FStackRunnable r) {
        ff_run0(() -> {
            try {
                r.run();
            } catch (Throwable t) {
                Logger.error(LogType.IMPROPER_USE, "exception thrown in ff_loop", t);
            }
        });
        // now the program can exit
        System.exit(0);
    }

    native private void ff_run0(Runnable r);
}
