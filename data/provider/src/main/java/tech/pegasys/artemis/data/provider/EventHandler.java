package tech.pegasys.artemis.data.provider;

import com.google.common.eventbus.Subscribe;
import tech.pegasys.artemis.data.IRecordAdapter;
import tech.pegasys.artemis.util.cli.CommandLineArguments;

import java.util.List;

public class EventHandler {
    List<String> events;
    FileProvider fileProvider;
    boolean isFormat;

    public EventHandler(CommandLineArguments cliArgs, FileProvider fileProvider) {
        this.events = cliArgs.getEvents();
        this.isFormat = cliArgs.isFormat();
        this.fileProvider = fileProvider;
    }

//    @Subscribe
//    public void onDataEvent(RawRecord record) {
//        TimeSeriesAdapter adapter = new TimeSeriesAdapter(record);
//        TimeSeriesRecord tsRecord = adapter.transform();
//        output(tsRecord);
//    }

    @Subscribe
    public void onEvent(IRecordAdapter record) {
        String name = record.getClass().getSimpleName();
        if(events.contains(name)) output(record);
    }

    private void output(IRecordAdapter record){
        if (isFormat) {
            fileProvider.formattedOutput(record);
        } else {
            fileProvider.serialOutput(record);
        }
    }
}
