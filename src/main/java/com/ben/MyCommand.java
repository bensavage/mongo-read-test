package com.ben;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class MyCommand implements MyBehaviour.Command {
    private int sequence;
}