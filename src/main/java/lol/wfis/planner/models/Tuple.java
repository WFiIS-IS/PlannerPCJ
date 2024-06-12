package lol.wfis.planner.models;

import java.io.Serializable;

import com.opencsv.bean.CsvBindByName;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Tuple implements Serializable {
    @CsvBindByName(column = "Id")
    private int id;
    @CsvBindByName(column = "Label")
    private String label;
    @CsvBindByName(column = "Room")
    private String room;
    @CsvBindByName(column = "Teacher")
    private String teacher;
}
