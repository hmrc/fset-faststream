<?php

error_reporting(E_ALL);

$csv = array_map('str_getcsv', file('../../fs-calendar-events/spreadsheets/2019-2020v4/newcastle.csv'));
//$csv = array_map('str_getcsv', file('../../fs-calendar-events/spreadsheets/2019-2020v4/london.csv'));

// This file reads input csv and generates yaml, which can be bulk uploaded into the system to create calendar events
// Run the file from the location you find it in the fset-faststream repo. All the paths are relative to its current location
// Use LibreOffice to open the Excel spreadsheet from the business, which contains 2 tabs: newcastle and london
// Save the newcastle tab as newcastle.csv and the london tab as london.csv
// Use LibreOffice so the correct Unix line endings are generated when saving as csv
// if you need to debug set the following on line 43:
// $debug = true;
//
// you usually need to check that all the skills specified in the spreadsheet are handled by the converter
// if any line has no skills specified we report an error to stdout eg.
// ERROR - line number: 4 has no skills specified bulk upload will not accept this
//
// Note also the descriptions are important. If they are not defined as below the events will not display in the calendar
// although they will bulk upload:
// PDFS -> PDFS - Skype interview
// EDIP -> EDIP - Telephone interview
// SDIP -> SDIP - Telephone interview

// Once you are happy with the output turn off the debugging
// and run the following commands so you generate the files for newcastle and london:
//
// php convert.php > ../../fs-calendar-events/output/newcastle.yaml
// php convert.php > ../../fs-calendar-events/output/london.yaml
//
// cd ../../fs-calendar-events/output
// cat newcastle.yaml london.yaml > event-schedule.yaml
// cp event-schedule.yaml ../../fset-faststream/conf
//
// the event-schedule.yaml file is what the system uses during the bulk upload
//
// upload urls:
// http://localhost:9289/fset-fast-stream-admin/assessment-events/save
//
// hit the backend directly:
// http POST :8101/candidate-application/events/save

$debug = false;

function venueNameToToken($venueName) {
    if ($venueName == 'Tyne View Park Newcastle') {
        return 'NEWCASTLE_FSAC';
    } else if ($venueName == '100 Parliament Street') {
        return 'LONDON_FSAC';
    } else if ($venueName == 'Skype' || $venueName == 'Telephone') {
        return 'VIRTUAL';
    } else if ($venueName == 'FCO King Charles Street') {
        return 'LONDON_FSB';
    }
}

function zeroOrValue($val) {
    if (trim($val) == '') { return '0'; } else { return trim($val); }
}

function formatTime($timeStr) {
    if (strlen($timeStr) > 5 && substr($timeStr, -3) == ':00') {
        return substr($timeStr, 0, -3);
    }
    return $timeStr;
}

function includeSkillIfNotZero($skill, $skillValue) {
    console("Reading skills from csv - $skill: $skillValue");
    if ($skillValue >= 1) {
        return '    '.$skill.': '.$skillValue."\n";
    }
}

function checkVirtualDescription($lineCount, $eventDesc) {
    $descriptions = array("PDFS"=>"PDFS - Skype interview", "EDIP"=>"EDIP - Telephone interview", "SDIP"=>"SDIP - Telephone interview");

    if (array_key_exists($eventDesc , $descriptions)) {
        console("ERROR - line number: $lineCount event description <<$eventDesc>> is invalid. It should be <<$descriptions[$eventDesc]>>");
    }
}

function checkVirtualLocation($lineCount, $venueName, $locationName) {
    if (($venueName == 'Skype' || $venueName == 'Telephone') && $locationName != "Virtual") {
        console("ERROR - line number: $lineCount location <<$locationName>> is invalid. It should be <<Virtual>>");
    }
}

function allSessionsEmpty($sessions) {
    for ($i = 0; $i < count($sessions); $i++) {
        $oneSession = $sessions[$i];
        $sessionNumber = $i + 1;
        console("Session $sessionNumber:  $oneSession[0], $oneSession[1], $oneSession[2], $oneSession[3], $oneSession[4]");
        // If a single session has some data then all sessions are not empty
        if (!($oneSession[0] == "" && $oneSession[1] == "" && $oneSession[2] == 0 && $oneSession[3] == 0 && $oneSession[4] == 0)) {
           return false;
        }
    }
    return true;
}

function allSkillsZero($skillValues) {
    for ($i = 0; $i < count($skillValues); $i++) {
        if ($skillValues[$i] != 0) return false;
    }
    return true;
}

function console($text) {
    global $debug;
    if ($debug) {
        echo "$text\n";
    }
}

$ignoreFirst = true;

console("Conversion started");

// Set this to 2 to allow for the first row being the column headings and to match the spreadsheet row numbers
$lineCount = 2;
foreach ($csv as $line) {

    if ($ignoreFirst) {
        $ignoreFirst = false;
        continue;
    }

    console("Processing line number: $lineCount (line 1 contains the column headers)");
    if ($debug) {
        print_r($line);
    }

    $i = 0;
    $eventType = $line[$i++];
    if (trim($eventType) != '') {
        $eventDesc = $line[$i++];
        checkVirtualDescription($lineCount, $eventDesc);
        if ($eventDesc == '-' || $eventDesc == '') {
            if ($eventType == 'FSAC') {
                $eventDesc = 'Fast Stream Assessment Centre';
            }
        }
        $eventLocation = $line[$i++];
        $eventVenue = $line[$i++];
        if (venueNameToToken($eventVenue) == "") {
            console("ERROR - line number: $lineCount has an invalid venue specified <<$eventVenue>>");
        }
        checkVirtualLocation($lineCount, $eventVenue, $eventLocation);
        $eventDate = $line[$i++];
        $eventStartTime = formatTime($line[$i++]);
        $eventEndTime = formatTime($line[$i++]);

        $fsacAssessors = zeroOrValue($line[$i++]);
        $qacAssessors = zeroOrValue($line[$i++]);
        $writtenExerciseAssessors = zeroOrValue($line[$i++]);

        $sdipQacs = zeroOrValue($line[$i++]); //new column 2018/19
        $edipQacs = zeroOrValue($line[$i++]); //new column 2018/19

        $datAssessors = zeroOrValue($line[$i++]);
        $oracExerciseMarkers = zeroOrValue($line[$i++]); //new column 2018/19
        $oracQacs = zeroOrValue($line[$i++]); //new column 2018/19

        $fcoAssessors = zeroOrValue($line[$i++]);
        $gcfsAssessors = zeroOrValue($line[$i++]);
        $eacAssessors = zeroOrValue($line[$i++]);
        $eacDsAssessors = zeroOrValue($line[$i++]);

        $sacAssessors = zeroOrValue($line[$i++]);
        $sacExerciseMarkers = zeroOrValue($line[$i++]); //new column 2018/19
        $sacSams = zeroOrValue($line[$i++]); //new column 2018/19

        $hopAssessors = zeroOrValue($line[$i++]);
        $pdfsAssessors = zeroOrValue($line[$i++]);
        $sefsAssessors = zeroOrValue($line[$i++]);
        $edipAssessors = zeroOrValue($line[$i++]);
        $sdipAssessors = zeroOrValue($line[$i++]);
        $sracAssessors = zeroOrValue($line[$i++]);
        $oracAssessors = zeroOrValue($line[$i++]);

        $departmentAssessors = zeroOrValue($line[$i++]);
        $chairAssessors = zeroOrValue($line[$i++]);
        $sifterAssessors = zeroOrValue($line[$i++]);

        $session1 = array(formatTime($line[$i++]), formatTime($line[$i++]), zeroOrValue($line[$i++]), zeroOrValue($line[$i++]), zeroOrValue($line[$i++]));
        $session2 = array(formatTime($line[$i++]), formatTime($line[$i++]), zeroOrValue($line[$i++]), zeroOrValue($line[$i++]), zeroOrValue($line[$i++]));
        $session3 = array(formatTime($line[$i++]), formatTime($line[$i++]), zeroOrValue($line[$i++]), zeroOrValue($line[$i++]), zeroOrValue($line[$i++]));
        $session4 = array(formatTime($line[$i++]), formatTime($line[$i++]), zeroOrValue($line[$i++]), zeroOrValue($line[$i++]), zeroOrValue($line[$i++]));
        $session5 = array(formatTime($line[$i++]), formatTime($line[$i++]), zeroOrValue($line[$i++]), zeroOrValue($line[$i++]), zeroOrValue($line[$i++]));
        $session6 = array(formatTime($line[$i++]), formatTime($line[$i++]), zeroOrValue($line[$i++]), zeroOrValue($line[$i++]), zeroOrValue($line[$i++]));
// 7 & 8 are needed for london not newcastle
//        $session7 = array(formatTime($line[$i++]), formatTime($line[$i++]), zeroOrValue($line[$i++]), zeroOrValue($line[$i++]), zeroOrValue($line[$i++]));
//        $session8 = array(formatTime($line[$i++]), formatTime($line[$i++]), zeroOrValue($line[$i++]), zeroOrValue($line[$i++]), zeroOrValue($line[$i++]));
//        $sessions = array($session1,$session2,$session3,$session4,$session5,$session6,$session7,$session8);
        $sessions = array($session1,$session2,$session3,$session4,$session5,$session6);

        if (allSessionsEmpty($sessions)) {
            console("ERROR - line number: $lineCount has no sessions specified - bulk upload will not accept this");
        }
        if (allSkillsZero(array($fsacAssessors, $chairAssessors, $departmentAssessors, $writtenExerciseAssessors, $datAssessors, $fcoAssessors,
            $gcfsAssessors, $eacAssessors, $eacDsAssessors, $sacAssessors, $sacExerciseMarkers, $sacSams, $hopAssessors, $pdfsAssessors,
            $sefsAssessors, $edipAssessors, $sdipAssessors, $sracAssessors, $oracAssessors, $oracExerciseMarkers, $oracQacs, $qacAssessors,
            $sifterAssessors, $sdipQacs, $edipQacs))) {
            console("ERROR - line number: $lineCount has no skills specified bulk - upload will not accept this");
        };

        $out =
            "- eventType: $eventType
  description: $eventDesc
  location: $eventLocation
  venue: ".venueNameToToken($eventVenue)."
  date: $eventDate
  capacity: 36
  minViableAttendees: 12
  attendeeSafetyMargin: 2
  startTime: $eventStartTime
  endTime: $eventEndTime
  skillRequirements:
".includeSkillIfNotZero('ASSESSOR', $fsacAssessors).includeSkillIfNotZero('CHAIR', $chairAssessors).includeSkillIfNotZero('DEPARTMENTAL_ASSESSOR', $departmentAssessors).includeSkillIfNotZero('EXERCISE_MARKER', $writtenExerciseAssessors).includeSkillIfNotZero('DAT_ASSESSOR', $datAssessors).includeSkillIfNotZero('FCO_ASSESSOR', $fcoAssessors).includeSkillIfNotZero('GCFS_ASSESSOR', $gcfsAssessors).includeSkillIfNotZero('EAC_ASSESSOR', $eacAssessors).includeSkillIfNotZero('EAC_DS_ASSESSOR', $eacDsAssessors).includeSkillIfNotZero('SAC_ASSESSOR', $sacAssessors).includeSkillIfNotZero('SAC_EM_ASSESSOR', $sacExerciseMarkers).includeSkillIfNotZero('SAC_SAM_ASSESSOR', $sacSams).includeSkillIfNotZero('HOP_ASSESSOR', $hopAssessors).includeSkillIfNotZero('PDFS_ASSESSOR', $pdfsAssessors).includeSkillIfNotZero('SEFS_ASSESSOR', $sefsAssessors).includeSkillIfNotZero('EDIP_ASSESSOR', $edipAssessors).includeSkillIfNotZero('EDIP_QAC', $edipQacs).includeSkillIfNotZero('SDIP_ASSESSOR', $sdipAssessors).includeSkillIfNotZero('SDIP_QAC', $sdipQacs).includeSkillIfNotZero('SRAC_ASSESSOR', $sracAssessors).includeSkillIfNotZero('ORAC_ASSESSOR', $oracAssessors).includeSkillIfNotZero('ORAC_EM_ASSESSOR', $oracExerciseMarkers).includeSkillIfNotZero('ORAC_QAC', $oracQacs).includeSkillIfNotZero('QUALITY_ASSURANCE_COORDINATOR', $qacAssessors).includeSkillIfNotZero('SIFTER', $sifterAssessors)."  sessions:\n";
        $sessionCounter = 1;

        foreach ($sessions as $session) {
            if ($session[0] != '') {
                $out = $out . "    - description: Session ".($sessionCounter++)."
      capacity: ".$session[2]."
      minViableAttendees: ".$session[3]."
      attendeeSafetyMargin: ".$session[4]."
      startTime: ".$session[0]."
      endTime: ".$session[1]."\n";
            }
        }

        echo $out."\n";
    }
    $lineCount++;
}

?>

