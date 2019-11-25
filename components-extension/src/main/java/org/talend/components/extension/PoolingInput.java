package org.talend.components.extension;

import lombok.RequiredArgsConstructor;
import org.talend.sdk.component.runtime.input.Input;

@RequiredArgsConstructor
public class PoolingInput implements Input {
    private final PollingConfiguration pollingConfiguration;
    private final Input input;

    @Override
    public Object next() {
        // todo: use pollingConfiguration
        return input.next();
    }

    @Override
    public String plugin() {
        return input.plugin();
    }

    @Override
    public String rootName() {
        return input.rootName();
    }

    @Override
    public String name() {
        return input.name();
    }

    @Override
    public void start() {
        input.start();
    }

    @Override
    public void stop() {
        input.stop();
    }
}
