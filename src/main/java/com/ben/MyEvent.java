package com.ben;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class MyEvent implements MyBehaviour.Event {
    private int sequence;
}