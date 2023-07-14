package ca.ualberta.autowise;

import io.vertx.core.Launcher;

public class AutoWiSELauncher extends Launcher {

    @Override
    protected String getMainVerticle() {
        return "ca.ualberta.autowise.AutoWiSE";
    }
}
