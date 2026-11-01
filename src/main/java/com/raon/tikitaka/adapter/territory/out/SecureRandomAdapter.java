package com.raon.tikitaka.adapter.territory.out;

import com.raon.tikitaka.application.territory.out.RandomPort;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Collections;
import java.util.List;

@Component
public class SecureRandomAdapter implements RandomPort {

    private final SecureRandom random = new SecureRandom();

    @Override
    public double nextDouble() {
        return random.nextDouble();
    }

    @Override
    public <T> void shuffle(List<T> list) {
        Collections.shuffle(list, random);
    }
}
