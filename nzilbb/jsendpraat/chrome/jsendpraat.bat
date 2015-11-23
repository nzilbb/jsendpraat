@echo off
REM
REM Copyright 2015 New Zealand Institute of Language, Brain and Behaviour, 
REM University of Canterbury
REM Written by Robert Fromont - robert.fromont@canterbury.ac.nz
REM
REM    This file is part of jsendpraat.
REM
REM    jsendpraat is free software; you can redistribute it and/or modify
REM    it under the terms of the GNU General Public License as published by
REM    the Free Software Foundation; either version 2 of the License, or
REM    (at your option) any later version.
REM
REM    jsendpraat is distributed in the hope that it will be useful,
REM    but WITHOUT ANY WARRANTY; without even the implied warranty of
REM    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
REM    GNU General Public License for more details.
REM
REM    You should have received a copy of the GNU General Public License
REM    along with jsendpraat; if not, write to the Free Software
REM    Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
REM
REM Runs the jsendpraat.jar chrome extension host for praat integration
java -jar "${jarpath}" %*
