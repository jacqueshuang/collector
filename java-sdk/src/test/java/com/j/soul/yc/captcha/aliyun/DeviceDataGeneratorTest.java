package com.j.soul.yc.captcha.aliyun;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DeviceDataGeneratorTest {

    @Test
    void deviceData_matchesPython() {
        assertEquals(
                "SKUnL2cTRSYG+LLXVRj3dQn6QqS1M57myLuJJfd/djB/JQwzBD4lv8jdraKIolFcRyBBMc0kPS+Zb2objaY0SbUOCSOIOZFNchkFi4BjyIhcq+N4oFzQHCFjX5r1BvGVrYyehCz0c/H+q4S89SQgdAIeNZaCYNhSOPgRT1s/a5k=",
                DeviceDataGenerator.generate("1uu8u2", "1pn9314j"));
    }
}
