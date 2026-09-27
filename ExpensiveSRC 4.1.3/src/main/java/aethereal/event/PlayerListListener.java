package aethereal.event;
import aethereal.type.EventPriority;
import aethereal.Expensive;
import aethereal.type.Mc;
import aethereal.util.StaffDetector;

public class PlayerListListener implements ClientListener {
    public PlayerListListener() {
        Expensive.INSTANCE.eventDispatcher().register(TabListEntryEvent.class, class038Var -> {
            if (Mc.INSTANCE.isWorldLoaded()) {
                StaffDetector.onPlayerListEvent(class038Var);
            }
        }, EventPriority.LOW);
    }
}
