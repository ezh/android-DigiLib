#!/usr/bin/env perl
use Term::ANSIColor;

select STDERR; $| = 1; # make unbuffered
select STDOUT; $| = 1; # make unbuffered
open ADBIN, "adb logcat -v threadtime |" or die "Can't execute adb :$!";
select ADBIN; $| = 1;
select STDOUT;
print color 'reset';
while (my $str = <ADBIN>) {
  my @data = split(' ',$str);
  $date = $data[1];
  $pid = sprintf("P%04d", $data[2]);
  $tid = sprintf("T%04d", $data[3]);
  $level = $data[4];
  $source = sprintf("%-24s", $data[5]);
  if ($date =~ /^[0-9:\.]{12}$/) {
    shift(@data);
    shift(@data);
    shift(@data);
    shift(@data);
    shift(@data);
    shift(@data);
    if (substr($source, 0, 1) eq "@") {
      if ($level eq "E") {
        print($date . " " . $pid . " " .  $tid . " " . $level . " " . $source . colored(join(" ",@data), "red on_black") . "\n");
      } elsif ($level eq "W") {
        print($date . " " . $pid . " " .  $tid . " " . $level . " " . $source . colored(join(" ",@data), "yellow on_black") . "\n");
      } elsif ($level eq "I") {
        print($date . " " . $pid . " " .  $tid . " " . $level . " " . $source . colored(join(" ",@data), "bold blue") . "\n");
      } elsif ($level eq "V") {
        print($date . " " . $pid . " " .  $tid . " " . $level . " " . $source . colored(join(" ",@data), "bold black") . "\n");
      } else {
        print($date . " " . $pid . " " .  $tid . " " . $level . " " . $source . colored(join(" ",@data), "bold") . "\n");
      };
    } else {
      print($date . " " . $pid . " " .  $tid . " " . $level . " " . $source . join(" ",@data) . "\n");
    };
  } else {
    print(join(" ",@data) . "\n");
  };
}
close ADBIN;